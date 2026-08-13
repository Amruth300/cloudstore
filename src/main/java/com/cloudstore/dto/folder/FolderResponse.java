package com.cloudstore.dto.folder;

import java.time.Instant;
import java.util.UUID;

public record FolderResponse(
        UUID id,
        String name,
        UUID parentId,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt
) {
}
