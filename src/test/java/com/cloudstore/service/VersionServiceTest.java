package com.cloudstore.service;

import com.cloudstore.dto.version.FileVersionResponse;
import com.cloudstore.entity.FileEntity;
import com.cloudstore.entity.FileVersion;
import com.cloudstore.entity.Role;
import com.cloudstore.entity.User;
import com.cloudstore.entity.enums.RoleName;
import com.cloudstore.exception.AccessDeniedCustomException;
import com.cloudstore.exception.ResourceNotFoundException;
import com.cloudstore.repository.FileVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    @Mock
    private FileVersionRepository fileVersionRepository;
    @Mock
    private FileService fileService;
    @Mock
    private StorageService storageService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private EventPublisherService eventPublisherService;

    @InjectMocks
    private VersionService versionService;

    private User owner;
    private User viewerUser;
    private FileEntity file;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(1L).name(RoleName.CUSTOMER).build();
        owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").role(role).build();
        viewerUser = User.builder().id(UUID.randomUUID()).email("viewer@example.com").role(role).build();

        file = FileEntity.builder().id(UUID.randomUUID()).name("report.docx").owner(owner)
                .currentVersion(1).storageKey("v1key").contentType("application/msword").sizeBytes(100L).build();
    }

    @Test
    void uploadNewVersion_incrementsVersionNumber_andUpdatesFilePointer() {
        MockMultipartFile mf = new MockMultipartFile("file", "v2.docx", "application/msword", "content".getBytes());

        when(fileService.getActiveFileOrThrow(file.getId())).thenReturn(file);
        when(storageService.buildStorageKey(anyString(), anyString(), eq(2))).thenReturn("v2key");
        when(storageService.upload(eq("v2key"), any(org.springframework.web.multipart.MultipartFile.class))).thenReturn("v2key");
        when(fileVersionRepository.save(any(FileVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        FileVersionResponse response = versionService.uploadNewVersion(owner, file.getId(), mf);

        assertThat(response.versionNumber()).isEqualTo(2);
        assertThat(file.getCurrentVersion()).isEqualTo(2);
        assertThat(file.getStorageKey()).isEqualTo("v2key");
        verify(eventPublisherService).publishFileVersionCreated(any());
    }

    @Test
    void uploadNewVersion_deniedForViewOnlyUser() {
        MockMultipartFile mf = new MockMultipartFile("file", "v2.docx", "application/msword", "content".getBytes());

        when(fileService.getActiveFileOrThrow(file.getId())).thenReturn(file);
        doThrow(new AccessDeniedCustomException("denied")).when(accessControlService).requireWriteAccess(viewerUser, file);

        assertThatThrownBy(() -> versionService.uploadNewVersion(viewerUser, file.getId(), mf))
                .isInstanceOf(AccessDeniedCustomException.class);

        verifyNoInteractions(storageService);
    }

    @Test
    void restoreVersion_createsNewVersionPointingAtOldContent() {
        FileVersion oldVersion = FileVersion.builder().id(UUID.randomUUID()).file(file).versionNumber(1)
                .storageKey("v1key").sizeBytes(50L).contentType("application/msword").createdBy(owner).build();

        file.setCurrentVersion(3); // pretend we're on v3 and want to roll back to v1

        when(fileService.getActiveFileOrThrow(file.getId())).thenReturn(file);
        when(fileVersionRepository.findByFileAndVersionNumber(file, 1)).thenReturn(Optional.of(oldVersion));
        when(fileVersionRepository.save(any(FileVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        FileVersionResponse response = versionService.restoreVersion(owner, file.getId(), 1);

        assertThat(response.versionNumber()).isEqualTo(4); // new version, history preserved
        assertThat(file.getStorageKey()).isEqualTo("v1key");
        assertThat(file.getCurrentVersion()).isEqualTo(4);
    }

    @Test
    void restoreVersion_throwsNotFound_forUnknownVersionNumber() {
        when(fileService.getActiveFileOrThrow(file.getId())).thenReturn(file);
        when(fileVersionRepository.findByFileAndVersionNumber(file, 99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionService.restoreVersion(owner, file.getId(), 99))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
