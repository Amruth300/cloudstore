package com.cloudstore.service;

import com.cloudstore.dto.share.ShareRequest;
import com.cloudstore.dto.share.SharePermissionUpdateRequest;
import com.cloudstore.dto.share.ShareResponse;
import com.cloudstore.entity.FileEntity;
import com.cloudstore.entity.FileShare;
import com.cloudstore.entity.User;
import com.cloudstore.event.FileSharedEvent;
import com.cloudstore.exception.BadRequestException;
import com.cloudstore.exception.ResourceNotFoundException;
import com.cloudstore.repository.FileShareRepository;
import com.cloudstore.repository.UserRepository;
import com.cloudstore.util.Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareService {

    private final FileShareRepository fileShareRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final AccessControlService accessControlService;
    private final EventPublisherService eventPublisherService;

    @Transactional
    public ShareResponse shareFile(User owner, UUID fileId, ShareRequest request) {
        FileEntity file = fileService.getActiveFileOrThrow(fileId);
        accessControlService.requireOwner(owner, file);

        User target = userRepository.findByEmail(request.userEmail().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("No user found with that email"));

        if (target.getId().equals(owner.getId())) {
            throw new BadRequestException("You cannot share a file with yourself");
        }

        FileShare share = fileShareRepository.findByFileAndSharedWith(file, target)
                .map(existing -> {
                    existing.setPermission(request.permission());
                    return existing;
                })
                .orElseGet(() -> fileShareRepository.save(FileShare.builder()
                        .file(file)
                        .sharedWith(target)
                        .sharedBy(owner)
                        .permission(request.permission())
                        .build()));

        accessControlService.evictPermission(file, target);

        eventPublisherService.publishFileShared(
                new FileSharedEvent(file.getId(), owner.getId(), target.getId(), request.permission().name(), Instant.now()));

        return Mapper.toShareResponse(share);
    }

    @Transactional
    public ShareResponse updatePermission(User owner, UUID fileId, UUID shareId, SharePermissionUpdateRequest request) {
        FileEntity file = fileService.getActiveFileOrThrow(fileId);
        accessControlService.requireOwner(owner, file);

        FileShare share = fileShareRepository.findById(shareId)
                .filter(s -> s.getFile().getId().equals(fileId))
                .orElseThrow(() -> new ResourceNotFoundException("Share not found"));

        share.setPermission(request.permission());
        accessControlService.evictPermission(file, share.getSharedWith());
        return Mapper.toShareResponse(share);
    }

    @Transactional
    public void removeShare(User owner, UUID fileId, UUID shareId) {
        FileEntity file = fileService.getActiveFileOrThrow(fileId);
        accessControlService.requireOwner(owner, file);

        FileShare share = fileShareRepository.findById(shareId)
                .filter(s -> s.getFile().getId().equals(fileId))
                .orElseThrow(() -> new ResourceNotFoundException("Share not found"));

        User sharedWith = share.getSharedWith();
        fileShareRepository.delete(share);
        accessControlService.evictPermission(file, sharedWith);
    }

    @Transactional(readOnly = true)
    public List<ShareResponse> listShares(User owner, UUID fileId) {
        FileEntity file = fileService.getActiveFileOrThrow(fileId);
        accessControlService.requireOwner(owner, file);
        return fileShareRepository.findByFile(file).stream().map(Mapper::toShareResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<com.cloudstore.dto.file.FileResponse> listSharedWithMe(User user) {
        return fileShareRepository.findBySharedWith(user).stream()
                .map(share -> Mapper.toFileResponse(share.getFile()))
                .toList();
    }
}
