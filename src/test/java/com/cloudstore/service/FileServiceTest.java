package com.cloudstore.service;

import com.cloudstore.dto.file.FileResponse;
import com.cloudstore.entity.FileEntity;
import com.cloudstore.entity.Role;
import com.cloudstore.entity.User;
import com.cloudstore.entity.enums.FileStatus;
import com.cloudstore.entity.enums.RoleName;
import com.cloudstore.exception.AccessDeniedCustomException;
import com.cloudstore.exception.BadRequestException;
import com.cloudstore.exception.ResourceNotFoundException;
import com.cloudstore.repository.FileRepository;
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
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private FileVersionRepository fileVersionRepository;
    @Mock
    private FolderService folderService;
    @Mock
    private StorageService storageService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private EventPublisherService eventPublisherService;

    @InjectMocks
    private FileService fileService;

    private User owner;
    private User otherUser;
    private FileEntity ownedFile;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(1L).name(RoleName.CUSTOMER).build();
        owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").role(role).build();
        otherUser = User.builder().id(UUID.randomUUID()).email("other@example.com").role(role).build();

        ownedFile = FileEntity.builder()
                .id(UUID.randomUUID())
                .name("report.pdf")
                .contentType("application/pdf")
                .sizeBytes(1024L)
                .owner(owner)
                .status(FileStatus.ACTIVE)
                .currentVersion(1)
                .storageKey("users/x/files/y/v1/z")
                .build();
    }

    @Test
    void uploadFile_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> fileService.uploadFile(owner, empty, null))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(storageService);
    }

    @Test
    void uploadFile_persistsMetadataAndUploadsToStorage() {
        MockMultipartFile mf = new MockMultipartFile("file", "doc.txt", "text/plain", "hello world".getBytes());

        when(folderService.resolveFolderOrNull(owner, null)).thenReturn(null);
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> {
            FileEntity f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(UUID.randomUUID());
            }
            return f;
        });
        when(storageService.buildStorageKey(anyString(), anyString(), eq(1))).thenReturn("users/o/files/f/v1/abc");
        when(storageService.upload(eq("users/o/files/f/v1/abc"), any(org.springframework.web.multipart.MultipartFile.class)))
                .thenReturn("users/o/files/f/v1/abc");

        FileResponse response = fileService.uploadFile(owner, mf, null);

        assertThat(response.name()).isEqualTo("doc.txt");
        assertThat(response.ownerId()).isEqualTo(owner.getId());
        verify(fileVersionRepository).save(any());
        verify(eventPublisherService).publishFileUploaded(any());
    }

    @Test
    void getFileMetadata_deniesAccess_forUserWhoIsNotOwnerOrSharee() {
        when(fileRepository.findByIdAndStatus(ownedFile.getId(), FileStatus.ACTIVE)).thenReturn(Optional.of(ownedFile));
        doThrow(new AccessDeniedCustomException("denied"))
                .when(accessControlService).requireReadAccess(otherUser, ownedFile);

        assertThatThrownBy(() -> fileService.getFileMetadata(otherUser, ownedFile.getId()))
                .isInstanceOf(AccessDeniedCustomException.class);
    }

    @Test
    void getFileMetadata_succeeds_forOwner() {
        when(fileRepository.findByIdAndStatus(ownedFile.getId(), FileStatus.ACTIVE)).thenReturn(Optional.of(ownedFile));

        FileResponse response = fileService.getFileMetadata(owner, ownedFile.getId());

        assertThat(response.id()).isEqualTo(ownedFile.getId());
        verify(accessControlService).requireReadAccess(owner, ownedFile);
    }

    @Test
    void getFileMetadata_throwsNotFound_forTrashedOrMissingFile() {
        UUID missingId = UUID.randomUUID();
        when(fileRepository.findByIdAndStatus(missingId, FileStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getFileMetadata(owner, missingId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void trashFile_movesFileToTrashAndPublishesEvent() {
        when(fileRepository.findByIdAndStatus(ownedFile.getId(), FileStatus.ACTIVE)).thenReturn(Optional.of(ownedFile));

        FileResponse response = fileService.trashFile(owner, ownedFile.getId());

        assertThat(response.status()).isEqualTo(FileStatus.TRASHED);
        verify(eventPublisherService).publishFileDeleted(any());
    }

    @Test
    void restoreFile_onlyOwnerCanRestore() {
        ownedFile.setStatus(FileStatus.TRASHED);
        when(fileRepository.findByIdAndStatus(ownedFile.getId(), FileStatus.TRASHED)).thenReturn(Optional.of(ownedFile));
        doThrow(new AccessDeniedCustomException("denied")).when(accessControlService).requireOwner(otherUser, ownedFile);

        assertThatThrownBy(() -> fileService.restoreFile(otherUser, ownedFile.getId()))
                .isInstanceOf(AccessDeniedCustomException.class);
    }

    @Test
    void permanentlyDelete_requiresFileToBeTrashedFirst() {
        when(fileRepository.findByIdAndStatus(ownedFile.getId(), FileStatus.TRASHED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.permanentlyDelete(owner, ownedFile.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
