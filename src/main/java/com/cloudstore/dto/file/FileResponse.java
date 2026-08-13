package com.cloudstore.dto.file;

import com.cloudstore.entity.enums.FileStatus;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        UUID id,
        String name,
        String contentType,
        Long sizeBytes,
        UUID folderId,
        UUID ownerId,
        FileStatus status,
        Integer currentVersion,
        Instant createdAt,
        Instant updatedAt
) {
}
