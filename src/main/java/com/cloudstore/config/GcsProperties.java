package com.cloudstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gcs")
public record GcsProperties(
        String bucketName,
        String credentialsLocation,
        long signedUrlExpiryMinutes
) {
}
