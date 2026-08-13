package com.cloudstore.service;

import com.cloudstore.entity.FileEntity;
import com.cloudstore.entity.FileShare;
import com.cloudstore.entity.Role;
import com.cloudstore.entity.User;
import com.cloudstore.entity.enums.RoleName;
import com.cloudstore.entity.enums.SharePermission;
import com.cloudstore.exception.AccessDeniedCustomException;
import com.cloudstore.repository.FileShareRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock
    private FileShareRepository fileShareRepository;

    @InjectMocks
    private AccessControlService accessControlService;

    private User owner;
    private User strangerUser;
    private User sharedViewUser;
    private User sharedEditUser;
    private FileEntity file;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(1L).name(RoleName.CUSTOMER).build();
        owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").role(role).build();
        strangerUser = User.builder().id(UUID.randomUUID()).email("stranger@example.com").role(role).build();
        sharedViewUser = User.builder().id(UUID.randomUUID()).email("viewer@example.com").role(role).build();
        sharedEditUser = User.builder().id(UUID.randomUUID()).email("editor@example.com").role(role).build();

        file = FileEntity.builder().id(UUID.randomUUID()).name("secret.pdf").owner(owner).build();
    }

    @Test
    void owner_hasEditAccess_byDefault() {
        Optional<SharePermission> permission = accessControlService.getEffectivePermission(owner, file);
        assertThat(permission).contains(SharePermission.EDIT);
    }

    @Test
    void strangerWithNoShare_hasNoAccess() {
        when(fileShareRepository.findByFileAndSharedWith(file, strangerUser)).thenReturn(Optional.empty());

        Optional<SharePermission> permission = accessControlService.getEffectivePermission(strangerUser, file);

        assertThat(permission).isEmpty();
        assertThatThrownBy(() -> accessControlService.requireReadAccess(strangerUser, file))
                .isInstanceOf(AccessDeniedCustomException.class);
    }

    @Test
    void userSharedWithViewPermission_canReadButNotWrite() {
        FileShare share = FileShare.builder().file(file).sharedWith(sharedViewUser).sharedBy(owner)
                .permission(SharePermission.VIEW).build();
        when(fileShareRepository.findByFileAndSharedWith(file, sharedViewUser)).thenReturn(Optional.of(share));

        accessControlService.requireReadAccess(sharedViewUser, file); // should not throw

        assertThatThrownBy(() -> accessControlService.requireWriteAccess(sharedViewUser, file))
                .isInstanceOf(AccessDeniedCustomException.class);
    }

    @Test
    void userSharedWithEditPermission_canReadAndWrite() {
        FileShare share = FileShare.builder().file(file).sharedWith(sharedEditUser).sharedBy(owner)
                .permission(SharePermission.EDIT).build();
        when(fileShareRepository.findByFileAndSharedWith(file, sharedEditUser)).thenReturn(Optional.of(share));

        accessControlService.requireReadAccess(sharedEditUser, file);
        accessControlService.requireWriteAccess(sharedEditUser, file); // should not throw
    }

    @Test
    void requireOwner_throwsForNonOwner_evenIfShared() {
        FileShare share = FileShare.builder().file(file).sharedWith(sharedEditUser).sharedBy(owner)
                .permission(SharePermission.EDIT).build();
        when(fileShareRepository.findByFileAndSharedWith(file, sharedEditUser)).thenReturn(Optional.of(share));

        assertThatThrownBy(() -> accessControlService.requireOwner(sharedEditUser, file))
                .isInstanceOf(AccessDeniedCustomException.class);
    }
}
