package com.cloudstore.util;

import com.cloudstore.dto.file.FileResponse;
import com.cloudstore.dto.folder.FolderResponse;
import com.cloudstore.dto.share.ShareResponse;
import com.cloudstore.dto.version.FileVersionResponse;
import com.cloudstore.entity.FileEntity;
import com.cloudstore.entity.FileShare;
import com.cloudstore.entity.FileVersion;
import com.cloudstore.entity.Folder;

public final class Mapper {

    private Mapper() {
    }

    public static FolderResponse toFolderResponse(Folder folder) {
        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getParent() != null ? folder.getParent().getId() : null,
                folder.getOwner().getId(),
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }

    public static FileResponse toFileResponse(FileEntity file) {
        return new FileResponse(
                file.getId(),
                file.getName(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getFolder() != null ? file.getFolder().getId() : null,
                file.getOwner().getId(),
                file.getStatus(),
                file.getCurrentVersion(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }

    public static ShareResponse toShareResponse(FileShare share) {
        return new ShareResponse(
                share.getId(),
                share.getFile().getId(),
                share.getSharedWith().getEmail(),
                share.getPermission(),
                share.getCreatedAt()
        );
    }

    public static FileVersionResponse toVersionResponse(FileVersion version) {
        return new FileVersionResponse(
                version.getId(),
                version.getFile().getId(),
                version.getVersionNumber(),
                version.getSizeBytes(),
                version.getContentType(),
                version.getCreatedBy().getEmail(),
                version.getCreatedAt()
        );
    }
}
