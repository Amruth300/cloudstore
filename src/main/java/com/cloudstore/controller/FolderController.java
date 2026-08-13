package com.cloudstore.controller;

import com.cloudstore.dto.folder.FolderRenameRequest;
import com.cloudstore.dto.folder.FolderRequest;
import com.cloudstore.dto.folder.FolderResponse;
import com.cloudstore.entity.User;
import com.cloudstore.security.CurrentUserProvider;
import com.cloudstore.service.FolderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/folders")
@RequiredArgsConstructor
@Tag(name = "Folders")
public class FolderController {

    private final FolderService folderService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<FolderResponse> create(@Valid @RequestBody FolderRequest request) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(folderService.createFolder(user, request));
    }

    @GetMapping
    public ResponseEntity<List<FolderResponse>> list(@RequestParam(required = false) UUID parentId) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(folderService.listFolders(user, parentId));
    }

    @PatchMapping("/{folderId}")
    public ResponseEntity<FolderResponse> rename(@PathVariable UUID folderId, @Valid @RequestBody FolderRenameRequest request) {
        User user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(folderService.renameFolder(user, folderId, request));
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> delete(@PathVariable UUID folderId) {
        User user = currentUserProvider.getCurrentUser();
        folderService.deleteFolder(user, folderId);
        return ResponseEntity.noContent().build();
    }
}
