package com.cloudstore.service;

import com.cloudstore.dto.share.ShareRequest;
import com.cloudstore.entity.FileEntity;
import com.cloudstore.entity.FileShare;
import com.cloudstore.entity.Role;
import com.cloudstore.entity.User;
import com.cloudstore.entity.enums.FileStatus;
import com.cloudstore.entity.enums.RoleName;
import com.cloudstore.entity.enums.SharePermission;
import com.cloudstore.exception.AccessDeniedCustomException;
import com.cloudstore.exception.BadRequestException;
import com.cloudstore.exception.ResourceNotFoundException;
import com.cloudstore.repository.FileShareRepository;
import com.cloudstore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private FileShareRepository fileShareRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileService fileService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private EventPublisherService eventPublisherService;

    @InjectMocks
    private ShareService shareService;

    private User owner;
    private User otherUser;
    private User targetUser;
    private FileEntity file;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(1L).name(RoleName.CUSTOMER).build();
        owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").role(role).build();
        otherUser = User.builder().id(UUID.randomUUID()).email("other@example.com").role(role).build();
        targetUser = User.builder().id(UUID.randomUUID()).email("target@example.com").role(role).build();

        file = FileEntity.builder().id(UUID.randomUUID()).name("doc.pdf").owner(owner)
                .status(FileStatus.ACTIVE).build();
    }

    @Test
    void shareFile_onlyOwnerCanShare() {
        when(fileService.getActiveFileOrThrow(file.getId())).thenReturn(file);
        doThrow(new AccessDeniedCustomException("denied")).when(accessControlService).requireOwner(otherUser, file);

        ShareRequest request = new ShareRequest("target@example.com", SharePermission.VIEW);

        assertThatThrownBy(() -> shareService.shareFile(otherUser, file.getId(), request))
                .isInstanceOf(AccessDeniedCustomException.class);
    }

    @Test
    void shareFile_cannotShareWithSelf() {
        when(fileService.getActiveFileOrThrow(file.getId())).thenReturn(file);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));

        ShareRequest request = new ShareRequest("owner@example.com", SharePermission.EDIT);

        assertThatThrownBy(() -> shareService.shareFile(owner, file.getId(), request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void shareFile_createsShareAndPublishesEvent() {
        when(fileService.getActiveFileOrThrow(file.getId())).thenReturn(file);
        when(userRepository.findByEmail("target@example.com")).thenReturn(Optional.of(targetUser));
        when(fileShareRepository.findByFileAndSharedWith(file, targetUser)).thenReturn(Optional.empty());
        when(fileShareRepository.save(any(FileShare.class))).thenAnswer(inv -> inv.getArgument(0));

        ShareRequest request = new ShareRequest("target@example.com", SharePermission.VIEW);
        var response = shareService.shareFile(owner, file.getId(), request);

        assertThat(response.sharedWithEmail()).isEqualTo("target@example.com");
        assertThat(response.permission()).isEqualTo(SharePermission.VIEW);
        verify(eventPublisherService).publishFileShared(any());
        verify(accessControlService).evictPermission(file, targetUser);
    }

    @Test
    void shareFile_updatesExistingSharePermission_insteadOfDuplicating() {
        FileShare existing = FileShare.builder().id(UUID.randomUUID()).file(file).sharedWith(targetUser)
                .sharedBy(owner).permission(SharePermission.VIEW).build();

        when(fileService.getActiveFileOrThrow(file.getId())).thenReturn(file);
        when(userRepository.findByEmail("target@example.com")).thenReturn(Optional.of(targetUser));
        when(fileShareRepository.findByFileAndSharedWith(file, targetUser)).thenReturn(Optional.of(existing));

        ShareRequest request = new ShareRequest("target@example.com", SharePermission.EDIT);
        var response = shareService.shareFile(owner, file.getId(), request);

        assertThat(response.permission()).isEqualTo(SharePermission.EDIT);
        verify(fileShareRepository, never()).save(any());
    }

    @Test
    void removeShare_onlyOwnerCanRemove() {
        FileShare existing = FileShare.builder().id(UUID.randomUUID()).file(file).sharedWith(targetUser)
                .sharedBy(owner).permission(SharePermission.VIEW).build();

        when(fileService.getActiveFileOrThrow(file.getId())).thenReturn(file);
        doThrow(new AccessDeniedCustomException("denied")).when(accessControlService).requireOwner(otherUser, file);

        assertThatThrownBy(() -> shareService.removeShare(otherUser, file.getId(), existing.getId()))
                .isInstanceOf(AccessDeniedCustomException.class);
    }

    @Test
    void removeShare_throwsNotFound_whenShareDoesNotBelongToFile() {
        when(fileService.getActiveFileOrThrow(file.getId())).thenReturn(file);
        UUID randomShareId = UUID.randomUUID();
        when(fileShareRepository.findById(randomShareId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareService.removeShare(owner, file.getId(), randomShareId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
