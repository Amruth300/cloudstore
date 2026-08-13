package com.cloudstore.service;

import com.cloudstore.config.RedisConfig;
import com.cloudstore.dto.file.FileMoveRequest;
import com.cloudstore.dto.file.FileRenameRequest;
import com.cloudstore.dto.file.FileResponse;
import com.cloudstore.dto.file.SignedUrlResponse;
import com.cloudstore.entity.FileEntity;
import com.cloudstore.entity.FileVersion;
import com.cloudstore.entity.Folder;
import com.cloudstore.entity.User;
import com.cloudstore.entity.enums.FileStatus;
import com.cloudstore.event.FileDeletedEvent;
import com.cloudstore.event.FileUploadedEvent;
import com.cloudstore.exception.BadRequestException;
import com.cloudstore.exception.ResourceNotFoundException;
import com.cloudstore.repository.FileRepository;
import com.cloudstore.repository.FileVersionRepository;
import com.cloudstore.util.Mapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    // Practical safety limits enforced at the application layer in addition to
    // Spring's multipart size limits (see application.yml).
    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024; // 100 MB per normal upload
    private static final Set<String> BLOCKED_CONTENT_TYPES = Set.of(
            "application/x-msdownload", "application/x-sh", "application/x-bat"
    );

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final FolderService folderService;
    private final StorageService storageService;
    private final AccessControlService accessControlService;
    private final EventPublisherService eventPublisherService;

    @Transactional
    @CacheEvict(cacheNames = RedisConfig.FOLDER_LISTING_CACHE, allEntries = true)
    public FileResponse uploadFile(User owner, MultipartFile multipartFile, UUID folderId) {
        validateFile(multipartFile);
        Folder folder = folderService.resolveFolderOrNull(owner, folderId);

        FileEntity file = FileEntity.builder()
                .name(multipartFile.getOriginalFilename() != null ? multipartFile.getOriginalFilename() : "untitled")
                .contentType(multipartFile.getContentType() != null ? multipartFile.getContentType() : "application/octet-stream")
                .sizeBytes(multipartFile.getSize())
                .owner(owner)
                .folder(folder)
                .status(FileStatus.ACTIVE)
                .currentVersion(1)
                .storageKey("pending")
                .build();
        file = fileRepository.save(file);

        String storageKey = storageService.buildStorageKey(owner.getId().toString(), file.getId().toString(), 1);
        storageService.upload(storageKey, multipartFile);
        file.setStorageKey(storageKey);

        FileVersion version = FileVersion.builder()
                .file(file)
                .versionNumber(1)
                .storageKey(storageKey)
                .sizeBytes(multipartFile.getSize())
                .contentType(file.getContentType())
                .createdBy(owner)
                .build();
        fileVersionRepository.save(version);

        eventPublisherService.publishFileUploaded(
                new FileUploadedEvent(file.getId(), owner.getId(), file.getName(), file.getSizeBytes(), Instant.now()));

        return Mapper.toFileResponse(file);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File exceeds the maximum allowed size of 100MB. Use chunked upload for larger files.");
        }
        if (file.getContentType() != null && BLOCKED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("This file type is not permitted");
        }
    }

    @Transactional(readOnly = true)
    public FileResponse getFileMetadata(User user, UUID fileId) {
        FileEntity file = getActiveFileOrThrow(fileId);
        accessControlService.requireReadAccess(user, file);
        return Mapper.toFileResponse(file);
    }

    @Transactional(readOnly = true)
    public InputStream downloadFile(User user, UUID fileId) {
        FileEntity file = getActiveFileOrThrow(fileId);
        accessControlService.requireReadAccess(user, file);
        return storageService.download(file.getStorageKey());
    }

    @Transactional(readOnly = true)
    public SignedUrlResponse getSignedDownloadUrl(User user, UUID fileId) {
        FileEntity file = getActiveFileOrThrow(fileId);
        accessControlService.requireReadAccess(user, file);
        Duration validity = Duration.ofMinutes(15);
        var url = storageService.generateSignedDownloadUrl(file.getStorageKey(), validity);
        return new SignedUrlResponse(url.toString(), Instant.now().plus(validity));
    }

    @Transactional
    public FileResponse renameFile(User user, UUID fileId, FileRenameRequest request) {
        FileEntity file = getActiveFileOrThrow(fileId);
        accessControlService.requireWriteAccess(user, file);
        file.setName(request.name());
        return Mapper.toFileResponse(file);
    }

    @Transactional
    @CacheEvict(cacheNames = RedisConfig.FOLDER_LISTING_CACHE, allEntries = true)
    public FileResponse moveFile(User user, UUID fileId, FileMoveRequest request) {
        FileEntity file = getActiveFileOrThrow(fileId);
        accessControlService.requireWriteAccess(user, file);
        Folder target = folderService.resolveFolderOrNull(file.getOwner(), request.targetFolderId());
        file.setFolder(target);
        return Mapper.toFileResponse(file);
    }

    @Transactional
    public FileResponse trashFile(User user, UUID fileId) {
        FileEntity file = getActiveFileOrThrow(fileId);
        accessControlService.requireWriteAccess(user, file);
        file.setStatus(FileStatus.TRASHED);
        file.setTrashedAt(Instant.now());
        eventPublisherService.publishFileDeleted(
                new FileDeletedEvent(file.getId(), file.getOwner().getId(), file.getName(), false, Instant.now()));
        return Mapper.toFileResponse(file);
    }

    @Transactional
    public FileResponse restoreFile(User user, UUID fileId) {
        FileEntity file = fileRepository.findByIdAndStatus(fileId, FileStatus.TRASHED)
                .orElseThrow(() -> new ResourceNotFoundException("File not found in trash"));
        accessControlService.requireOwner(user, file);
        file.setStatus(FileStatus.ACTIVE);
        file.setTrashedAt(null);
        return Mapper.toFileResponse(file);
    }

    @Transactional
    public void permanentlyDelete(User user, UUID fileId) {
        FileEntity file = fileRepository.findByIdAndStatus(fileId, FileStatus.TRASHED)
                .orElseThrow(() -> new ResourceNotFoundException("File not found in trash. Move it to trash first."));
        accessControlService.requireOwner(user, file);

        List<FileVersion> versions = fileVersionRepository.findByFileOrderByVersionNumberDesc(file);
        for (FileVersion version : versions) {
            storageService.delete(version.getStorageKey());
        }
        fileVersionRepository.deleteAll(versions);
        fileRepository.delete(file);

        eventPublisherService.publishFileDeleted(
                new FileDeletedEvent(fileId, user.getId(), file.getName(), true, Instant.now()));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RedisConfig.FOLDER_LISTING_CACHE,
            key = "'files:' + #owner.id + ':' + (#folderId != null ? #folderId : 'root') + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<FileResponse> listFiles(User owner, UUID folderId, Pageable pageable) {
        Page<FileEntity> page;
        if (folderId == null) {
            page = fileRepository.findByOwnerAndFolderIsNullAndStatus(owner, FileStatus.ACTIVE, pageable);
        } else {
            Folder folder = folderService.resolveFolderOrNull(owner, folderId);
            page = fileRepository.findByOwnerAndFolderAndStatus(owner, folder, FileStatus.ACTIVE, pageable);
        }
        return page.map(Mapper::toFileResponse);
    }

    @Transactional(readOnly = true)
    public Page<FileResponse> searchFiles(User owner, String name, String contentType, Pageable pageable) {
        return fileRepository.search(owner, FileStatus.ACTIVE, name, contentType, pageable).map(Mapper::toFileResponse);
    }

    @Transactional(readOnly = true)
    public Page<FileResponse> listTrash(User owner, Pageable pageable) {
        return fileRepository.findByOwnerAndStatus(owner, FileStatus.TRASHED, pageable).map(Mapper::toFileResponse);
    }

    FileEntity getActiveFileOrThrow(UUID fileId) {
        return fileRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }
}
