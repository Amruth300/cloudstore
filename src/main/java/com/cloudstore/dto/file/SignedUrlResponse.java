package com.cloudstore.dto.file;

import java.time.Instant;

public record SignedUrlResponse(
        String url,
        Instant expiresAt
) {
}
