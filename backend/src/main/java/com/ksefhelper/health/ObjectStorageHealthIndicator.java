package com.ksefhelper.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Component("objectStorage")
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class ObjectStorageHealthIndicator implements HealthIndicator {
    private final S3Client s3Client;
    private final String bucket;

    public ObjectStorageHealthIndicator(
            S3Client s3Client,
            @Value("${app.storage.s3.bucket}") String bucket
    ) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public Health health() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return Health.up().withDetail("bucket", bucket).build();
        } catch (RuntimeException ex) {
            return Health.down(ex).withDetail("bucket", bucket).build();
        }
    }
}
