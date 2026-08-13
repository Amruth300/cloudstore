package com.cloudstore.dto.folder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FolderRenameRequest(
        @NotBlank @Size(max = 255) String name
) {
}
