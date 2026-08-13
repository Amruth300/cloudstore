package com.cloudstore.dto.file;

import java.util.UUID;

/** targetFolderId == null means move to root */
public record FileMoveRequest(
        UUID targetFolderId
) {
}
