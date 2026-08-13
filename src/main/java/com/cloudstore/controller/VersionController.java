package com.cloudstore.controller;

import com.cloudstore.dto.version.FileVersionResponse;
import com.cloudstore.entity.User;
import com.cloudstore.security.CurrentUserProvider;
import com.cloudstore.service.VersionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files/{fileId}/versions")
@RequiredArgsConstructor
@Tag(name = "Versioning")
public class VersionController {

    private final VersionService versionService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<FileVersionResponse> uploadNewVersion(@PathVariable UUID fileId,
                                                                  @RequestParam("file") MultipartFile file) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(versionService.uploadNewVersion(user, fileId, file));
    }

    @GetMapping
    public ResponseEntity<List<FileVersionResponse>> listVersions(@PathVariable UUID fileId) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(versionService.listVersions(user, fileId));
    }

    @GetMapping("/{versionNumber}/download")
    public ResponseEntity<InputStreamResource> downloadVersion(@PathVariable UUID fileId, @PathVariable int versionNumber) {
        User user = currentUserProvider.getCurrentUser();
        InputStream content = versionService.downloadVersion(user, fileId, versionNumber);
        return ResponseEntity.ok().body(new InputStreamResource(content));
    }

    @PostMapping("/{versionNumber}/restore")
    public ResponseEntity<FileVersionResponse> restoreVersion(@PathVariable UUID fileId, @PathVariable int versionNumber) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(versionService.restoreVersion(user, fileId, versionNumber));
    }
}
