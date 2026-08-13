package com.cloudstore.dto.share;

import com.cloudstore.entity.enums.SharePermission;

import java.time.Instant;
import java.util.UUID;

public record ShareResponse(
        UUID id,
        UUID fileId,
        String sharedWithEmail,
        SharePermission permission,
        Instant createdAt
) {
}
