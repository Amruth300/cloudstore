package com.cloudstore.dto.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FileRenameRequest(
        @NotBlank @Size(max = 255) String name
) {
}
