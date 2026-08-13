package com.cloudstore.dto.share;

import com.cloudstore.entity.enums.SharePermission;
import jakarta.validation.constraints.NotNull;

public record SharePermissionUpdateRequest(
        @NotNull SharePermission permission
) {
}
