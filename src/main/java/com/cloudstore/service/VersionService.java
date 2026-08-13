package com.cloudstore.service;

import com.cloudstore.dto.version.FileVersionResponse;
import com.cloudstore.entity.FileEntity;
import com.cloudstore.entity.FileVersion;
import com.cloudstore.entity.User;
import com.cloudstore.event.FileVersionCreatedEvent;
import com.cloudstore.exception.BadRequestException;
import com.cloudstore.exception.ResourceNotFoundException;
import com.cloudstore.repository.FileVersionRepository;
import com.cloudstore.util.Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VersionService {

    private final FileVersionRepository fileVersionRepository;
    private final FileService fileService;
    private final StorageService storageService;
    private final AccessControlService accessControlService;
    private final EventPublisherService eventPublisherService;

    @Transactional
    public FileVersionResponse uploadNewVersion(User user, UUID fileId, MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BadRequestException("Uploaded file must not be empty");
        }
        FileEntity file = fileService.getActiveFileOrThrow(fileId);
        accessControlService.requireWriteAccess(user, file);

        int nextVersion = file.getCurrentVersion() + 1;
        String storageKey = storageService.buildStorageKey(file.getOwner().getId().toString(), file.getId().toString(), nextVersion);
        storageService.upload(storageKey, multipartFile);

        FileVersion version = FileVersion.builder()
                .file(file)
                .versionNumber(nextVersion)
                .storageKey(storageKey)
                .sizeBytes(multipartFile.getSize())
                .contentType(multipartFile.getContentType() != null ? multipartFile.getContentType() : file.getContentType())
                .createdBy(user)
                .build();
        fileVersionRepository.save(version);

        file.setCurrentVersion(nextVersion);
        file.setStorageKey(storageKey);
        file.setSizeBytes(multipartFile.getSize());
        file.setContentType(version.getContentType());

        eventPublisherService.publishFileVersionCreated(
                new FileVersionCreatedEvent(file.getId(), version.getId(), nextVersion, user.getId(), Instant.now()));

        return Mapper.toVersionResponse(version);
    }

    @Transactional(readOnly = true)
    public List<FileVersionResponse> listVersions(User user, UUID fileId) {
        FileEntity file = fileService.getActiveFileOrThrow(fileId);
        accessControlService.requireReadAccess(user, file);
        return fileVersionRepository.findByFileOrderByVersionNumberDesc(file).stream()
                .map(Mapper::toVersionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InputStream downloadVersion(User user, UUID fileId, int versionNumber) {
        FileEntity file = fileService.getActiveFileOrThrow(fileId);
        accessControlService.requireReadAccess(user, file);
        FileVersion version = fileVersionRepository.findByFileAndVersionNumber(file, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found"));
        return storageService.download(version.getStorageKey());
    }

    /**
     * Restores an older version by creating a NEW version entry that points at the
     * old version's content (git-style "revert"), preserving full history rather than
     * destructively overwriting it.
     */
    @Transactional
    public FileVersionResponse restoreVersion(User user, UUID fileId, int versionNumber) {
        FileEntity file = fileService.getActiveFileOrThrow(fileId);
        accessControlService.requireWriteAccess(user, file);

        FileVersion target = fileVersionRepository.findByFileAndVersionNumber(file, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found"));

        int nextVersion = file.getCurrentVersion() + 1;
        FileVersion restored = FileVersion.builder()
                .file(file)
                .versionNumber(nextVersion)
                .storageKey(target.getStorageKey()) // points at the same immutable GCS object
                .sizeBytes(target.getSizeBytes())
                .contentType(target.getContentType())
                .createdBy(user)
                .build();
        fileVersionRepository.save(restored);

        file.setCurrentVersion(nextVersion);
        file.setStorageKey(target.getStorageKey());
        file.setSizeBytes(target.getSizeBytes());
        file.setContentType(target.getContentType());

        eventPublisherService.publishFileVersionCreated(
                new FileVersionCreatedEvent(file.getId(), restored.getId(), nextVersion, user.getId(), Instant.now()));

        return Mapper.toVersionResponse(restored);
    }
}
