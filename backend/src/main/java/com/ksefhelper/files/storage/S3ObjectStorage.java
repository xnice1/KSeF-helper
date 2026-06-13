package com.ksefhelper.files.storage;

import com.ksefhelper.common.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3ObjectStorage implements ObjectStorage {
    private final S3Client s3Client;
    private final String bucket;
    private final String serverSideEncryption;
    private final String kmsKeyId;
    private final boolean permanentDeleteVersions;

    public S3ObjectStorage(
            S3Client s3Client,
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.s3.server-side-encryption:AES256}") String serverSideEncryption,
            @Value("${app.storage.s3.kms-key-id:}") String kmsKeyId,
            @Value("${app.storage.s3.permanent-delete-versions:false}") boolean permanentDeleteVersions
    ) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.serverSideEncryption = serverSideEncryption;
        this.kmsKeyId = kmsKeyId;
        this.permanentDeleteVersions = permanentDeleteVersions;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType, String checksum) {
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .metadata(Map.of("sha256", checksum));
        if (serverSideEncryption != null && !serverSideEncryption.isBlank()) {
            request.serverSideEncryption(ServerSideEncryption.fromValue(serverSideEncryption));
        }
        if (kmsKeyId != null && !kmsKeyId.isBlank()) {
            request.ssekmsKeyId(kmsKeyId);
        }
        s3Client.putObject(request.build(), RequestBody.fromBytes(bytes));
    }

    @Override
    public byte[] read(String key) {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try {
            writeTo(key, output);
        } catch (IOException ex) {
            throw new IllegalStateException("Stored XML file could not be read.", ex);
        }
        return output.toByteArray();
    }

    @Override
    public void writeTo(String key, OutputStream output) throws IOException {
        try {
            try (var response = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build())) {
                response.transferTo(output);
            }
        } catch (NoSuchKeyException ex) {
            throw new NotFoundException("Stored XML file was not found.");
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new NotFoundException("Stored XML file was not found.");
            }
            throw ex;
        }
    }

    @Override
    public void delete(String key) {
        if (!permanentDeleteVersions) {
            deleteCurrent(key);
            return;
        }

        List<ObjectIdentifier> versions = objectVersions(key);
        if (versions.isEmpty()) {
            deleteCurrent(key);
            return;
        }
        for (int start = 0; start < versions.size(); start += 1000) {
            List<ObjectIdentifier> batch = versions.subList(start, Math.min(start + 1000, versions.size()));
            var response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(batch).quiet(true).build())
                    .build());
            if (!response.errors().isEmpty()) {
                throw new IllegalStateException(
                        "S3 failed to permanently delete " + response.errors().size() + " object version(s)."
                );
            }
        }
        if (!objectVersions(key).isEmpty()) {
            throw new IllegalStateException("S3 object versions still exist after permanent deletion.");
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private List<ObjectIdentifier> objectVersions(String key) {
        List<ObjectIdentifier> identifiers = new ArrayList<>();
        String keyMarker = null;
        String versionIdMarker = null;
        boolean truncated;
        do {
            var response = s3Client.listObjectVersions(ListObjectVersionsRequest.builder()
                    .bucket(bucket)
                    .prefix(key)
                    .keyMarker(keyMarker)
                    .versionIdMarker(versionIdMarker)
                    .build());
            response.versions().stream()
                    .filter(version -> key.equals(version.key()))
                    .map(version -> ObjectIdentifier.builder().key(key).versionId(version.versionId()).build())
                    .forEach(identifiers::add);
            response.deleteMarkers().stream()
                    .filter(marker -> key.equals(marker.key()))
                    .map(marker -> ObjectIdentifier.builder().key(key).versionId(marker.versionId()).build())
                    .forEach(identifiers::add);
            truncated = Boolean.TRUE.equals(response.isTruncated());
            keyMarker = response.nextKeyMarker();
            versionIdMarker = response.nextVersionIdMarker();
        } while (truncated);
        return identifiers;
    }

    private void deleteCurrent(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
