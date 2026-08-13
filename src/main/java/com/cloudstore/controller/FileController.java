package com.cloudstore.controller;

import com.cloudstore.dto.file.FileMoveRequest;
import com.cloudstore.dto.file.FileRenameRequest;
import com.cloudstore.dto.file.FileResponse;
import com.cloudstore.dto.file.SignedUrlResponse;
import com.cloudstore.entity.User;
import com.cloudstore.security.CurrentUserProvider;
import com.cloudstore.service.FileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files")
public class FileController {

    private final FileService fileService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponse> upload(@RequestParam("file") MultipartFile file,
                                                @RequestParam(required = false) UUID folderId) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.status(201).body(fileService.uploadFile(user, file, folderId));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<FileResponse> getMetadata(@PathVariable UUID fileId) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(fileService.getFileMetadata(user, fileId));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID fileId) {
        User user = currentUserProvider.getCurrentUser();
        FileResponse metadata = fileService.getFileMetadata(user, fileId);
        InputStream content = fileService.downloadFile(user, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.name() + "\"")
                .body(new InputStreamResource(content));
    }

    @GetMapping("/{fileId}/signed-url")
    public ResponseEntity<SignedUrlResponse> signedUrl(@PathVariable UUID fileId) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(fileService.getSignedDownloadUrl(user, fileId));
    }

    @PatchMapping("/{fileId}/rename")
    public ResponseEntity<FileResponse> rename(@PathVariable UUID fileId, @Valid @RequestBody FileRenameRequest request) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(fileService.renameFile(user, fileId, request));
    }

    @PatchMapping("/{fileId}/move")
    public ResponseEntity<FileResponse> move(@PathVariable UUID fileId, @Valid @RequestBody FileMoveRequest request) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(fileService.moveFile(user, fileId, request));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<FileResponse> trash(@PathVariable UUID fileId) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(fileService.trashFile(user, fileId));
    }

    @PostMapping("/{fileId}/restore")
    public ResponseEntity<FileResponse> restore(@PathVariable UUID fileId) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(fileService.restoreFile(user, fileId));
    }

    @DeleteMapping("/{fileId}/permanent")
    public ResponseEntity<Void> permanentDelete(@PathVariable UUID fileId) {
        User user = currentUserProvider.getCurrentUser();
        fileService.permanentlyDelete(user, fileId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<FileResponse>> list(@RequestParam(required = false) UUID folderId, Pageable pageable) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(fileService.listFiles(user, folderId, pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<FileResponse>> search(@RequestParam(required = false) String name,
                                                       @RequestParam(required = false) String contentType,
                                                       Pageable pageable) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(fileService.searchFiles(user, name, contentType, pageable));
    }

    @GetMapping("/trash")
    public ResponseEntity<Page<FileResponse>> trashList(Pageable pageable) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(fileService.listTrash(user, pageable));
    }
}
