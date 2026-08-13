package com.cloudstore.dto.share;

import com.cloudstore.entity.enums.SharePermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ShareRequest(
        @NotBlank @Email String userEmail,
        @NotNull SharePermission permission
) {
}
