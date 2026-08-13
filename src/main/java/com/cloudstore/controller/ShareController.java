package com.cloudstore.controller;

import com.cloudstore.dto.file.FileResponse;
import com.cloudstore.dto.share.ShareRequest;
import com.cloudstore.dto.share.SharePermissionUpdateRequest;
import com.cloudstore.dto.share.ShareResponse;
import com.cloudstore.entity.User;
import com.cloudstore.security.CurrentUserProvider;
import com.cloudstore.service.ShareService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Sharing")
public class ShareController {

    private final ShareService shareService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/api/v1/files/{fileId}/shares")
    public ResponseEntity<ShareResponse> share(@PathVariable UUID fileId, @Valid @RequestBody ShareRequest request) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(shareService.shareFile(user, fileId, request));
    }

    @GetMapping("/api/v1/files/{fileId}/shares")
    public ResponseEntity<List<ShareResponse>> listShares(@PathVariable UUID fileId) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(shareService.listShares(user, fileId));
    }

    @PatchMapping("/api/v1/files/{fileId}/shares/{shareId}")
    public ResponseEntity<ShareResponse> updatePermission(@PathVariable UUID fileId, @PathVariable UUID shareId,
                                                            @Valid @RequestBody SharePermissionUpdateRequest request) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(shareService.updatePermission(user, fileId, shareId, request));
    }

    @DeleteMapping("/api/v1/files/{fileId}/shares/{shareId}")
    public ResponseEntity<Void> removeShare(@PathVariable UUID fileId, @PathVariable UUID shareId) {
        User user = currentUserProvider.getCurrentUser();
        shareService.removeShare(user, fileId, shareId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/shared-with-me")
    public ResponseEntity<List<FileResponse>> sharedWithMe() {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(shareService.listSharedWithMe(user));
    }
}
