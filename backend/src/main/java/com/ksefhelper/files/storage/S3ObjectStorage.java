package com.ksefhelper.files.storage;

import com.ksefhelper.common.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3ObjectStorage implements ObjectStorage {
    private final S3Client s3Client;
    private final String bucket;
    private final String serverSideEncryption;
    private final String kmsKeyId;

    public S3ObjectStorage(
            S3Client s3Client,
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.s3.server-side-encryption:AES256}") String serverSideEncryption,
            @Value("${app.storage.s3.kms-key-id:}") String kmsKeyId
    ) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.serverSideEncryption = serverSideEncryption;
        this.kmsKeyId = kmsKeyId;
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
        try {
            ResponseBytes<?> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return response.asByteArray();
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
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
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
}
