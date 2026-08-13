package com.cloudstore.service;

import com.cloudstore.config.RedisConfig;
import com.cloudstore.dto.folder.FolderRenameRequest;
import com.cloudstore.dto.folder.FolderRequest;
import com.cloudstore.dto.folder.FolderResponse;
import com.cloudstore.entity.Folder;
import com.cloudstore.entity.User;
import com.cloudstore.exception.BadRequestException;
import com.cloudstore.exception.DuplicateResourceException;
import com.cloudstore.exception.ResourceNotFoundException;
import com.cloudstore.repository.FolderRepository;
import com.cloudstore.util.Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;

    @Transactional
    @CacheEvict(cacheNames = RedisConfig.FOLDER_LISTING_CACHE, allEntries = true)
    public FolderResponse createFolder(User owner, FolderRequest request) {
        Folder parent = null;
        if (request.parentId() != null) {
            parent = folderRepository.findByIdAndOwner(request.parentId(), owner)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent folder not found"));
        }

        boolean exists = parent != null
                ? folderRepository.existsByNameAndParentAndOwnerAndDeletedFalse(request.name(), parent, owner)
                : folderRepository.existsByNameAndParentIsNullAndOwnerAndDeletedFalse(request.name(), owner);
        if (exists) {
            throw new DuplicateResourceException("A folder with this name already exists here");
        }

        Folder folder = Folder.builder()
                .name(request.name())
                .owner(owner)
                .parent(parent)
                .build();

        return Mapper.toFolderResponse(folderRepository.save(folder));
    }

    @Cacheable(cacheNames = RedisConfig.FOLDER_LISTING_CACHE, key = "#owner.id + ':' + (#parentId != null ? #parentId : 'root')")
    @Transactional(readOnly = true)
    public List<FolderResponse> listFolders(User owner, UUID parentId) {
        List<Folder> folders = parentId == null
                ? folderRepository.findByOwnerAndParentIsNullAndDeletedFalse(owner)
                : folderRepository.findByOwnerAndParentIdAndDeletedFalse(owner, parentId);
        return folders.stream().map(Mapper::toFolderResponse).toList();
    }

    @Transactional
    @CacheEvict(cacheNames = RedisConfig.FOLDER_LISTING_CACHE, allEntries = true)
    public FolderResponse renameFolder(User owner, UUID folderId, FolderRenameRequest request) {
        Folder folder = getOwnedFolder(owner, folderId);
        folder.setName(request.name());
        return Mapper.toFolderResponse(folder);
    }

    @Transactional
    @CacheEvict(cacheNames = RedisConfig.FOLDER_LISTING_CACHE, allEntries = true)
    public void deleteFolder(User owner, UUID folderId) {
        Folder folder = getOwnedFolder(owner, folderId);
        softDeleteRecursively(folder);
    }

    private void softDeleteRecursively(Folder folder) {
        folder.setDeleted(true);
        List<Folder> children = folderRepository.findByOwnerAndParentIdAndDeletedFalse(folder.getOwner(), folder.getId());
        for (Folder child : children) {
            softDeleteRecursively(child);
        }
    }

    private Folder getOwnedFolder(User owner, UUID folderId) {
        Folder folder = folderRepository.findByIdAndOwner(folderId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
        if (folder.isDeleted()) {
            throw new BadRequestException("Folder has been deleted");
        }
        return folder;
    }

    @Transactional(readOnly = true)
    public Folder resolveFolderOrNull(User owner, UUID folderId) {
        if (folderId == null) {
            return null;
        }
        return getOwnedFolder(owner, folderId);
    }
}
