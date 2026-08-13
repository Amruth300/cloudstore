package com.cloudstore.dto.version;

import java.time.Instant;
import java.util.UUID;

public record FileVersionResponse(
        UUID id,
        UUID fileId,
        Integer versionNumber,
        Long sizeBytes,
        String contentType,
        String createdByEmail,
        Instant createdAt
) {
}
