package com.ksefhelper.files.storage;

import com.ksefhelper.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3ObjectStorageTest {
    @Test
    void storesEncryptedObjectsWithChecksumMetadata() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, "invoices", "AES256", "", false);

        storage.put("org/file.xml", new byte[]{1, 2, 3}, "application/xml", "abc123");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("invoices");
        assertThat(request.getValue().key()).isEqualTo("org/file.xml");
        assertThat(request.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        assertThat(request.getValue().metadata()).containsEntry("sha256", "abc123");
    }

    @Test
    void readsObjectsAndMapsMissingObjectsToNotFound() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, "invoices", "AES256", "", false);
        byte[] bytes = new byte[]{4, 5, 6};
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(
                new ResponseInputStream<>(
                        GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new ByteArrayInputStream(bytes))
                )
        );

        assertThat(storage.read("org/file.xml")).isEqualTo(bytes);

        when(client.getObject(any(GetObjectRequest.class))).thenThrow(
                S3Exception.builder().statusCode(404).message("missing").build()
        );
        assertThatThrownBy(() -> storage.read("org/missing.xml"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void includesConfiguredKmsKey() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, "invoices", "aws:kms", "key-123", false);

        storage.put("org/file.xml", new byte[]{1}, "application/xml", "abc123");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(request.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AWS_KMS);
        assertThat(request.getValue().ssekmsKeyId()).isEqualTo("key-123");
    }

    @Test
    void permanentlyDeletesEveryVersionAndDeleteMarker() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, "invoices", "AES256", "", true);
        when(client.listObjectVersions(any(software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest.class)))
                .thenReturn(
                        versionResponse(),
                        ListObjectVersionsResponse.builder().isTruncated(false).build()
                );
        when(client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder().build());

        storage.delete("org/file.xml");

        ArgumentCaptor<DeleteObjectsRequest> request = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(client).deleteObjects(request.capture());
        assertThat(request.getValue().delete().objects())
                .extracting(object -> object.key() + ":" + object.versionId())
                .containsExactlyInAnyOrder(
                        "org/file.xml:v2",
                        "org/file.xml:v1",
                        "org/file.xml:marker"
                );
    }

    @Test
    void rejectsPartialFailuresFromPermanentVersionDeletion() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, "invoices", "AES256", "", true);
        when(client.listObjectVersions(any(software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest.class)))
                .thenReturn(versionResponse());
        when(client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder()
                        .errors(S3Error.builder()
                                .key("org/file.xml")
                                .versionId("v2")
                                .code("AccessDenied")
                                .build())
                        .build());

        assertThatThrownBy(() -> storage.delete("org/file.xml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed to permanently delete 1 object version");
    }

    private static ListObjectVersionsResponse versionResponse() {
        return ListObjectVersionsResponse.builder()
                .isTruncated(false)
                .versions(
                        ObjectVersion.builder().key("org/file.xml").versionId("v2").build(),
                        ObjectVersion.builder().key("org/file.xml").versionId("v1").build(),
                        ObjectVersion.builder().key("org/file.xml.backup").versionId("other").build()
                )
                .deleteMarkers(DeleteMarkerEntry.builder().key("org/file.xml").versionId("marker").build())
                .build();
    }
}
