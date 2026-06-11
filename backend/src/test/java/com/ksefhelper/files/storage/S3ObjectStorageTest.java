package com.ksefhelper.files.storage;

import com.ksefhelper.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

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
        S3ObjectStorage storage = new S3ObjectStorage(client, "invoices", "AES256", "");

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
        S3ObjectStorage storage = new S3ObjectStorage(client, "invoices", "AES256", "");
        byte[] bytes = new byte[]{4, 5, 6};
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes)
        );

        assertThat(storage.read("org/file.xml")).isEqualTo(bytes);

        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(
                S3Exception.builder().statusCode(404).message("missing").build()
        );
        assertThatThrownBy(() -> storage.read("org/missing.xml"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void includesConfiguredKmsKey() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, "invoices", "aws:kms", "key-123");

        storage.put("org/file.xml", new byte[]{1}, "application/xml", "abc123");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(request.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AWS_KMS);
        assertThat(request.getValue().ssekmsKeyId()).isEqualTo("key-123");
    }
}
