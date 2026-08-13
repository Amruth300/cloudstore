package com.cloudstore.service;

import com.cloudstore.config.RedisConfig;
import com.cloudstore.entity.FileEntity;
import com.cloudstore.entity.FileShare;
import com.cloudstore.entity.User;
import com.cloudstore.entity.enums.SharePermission;
import com.cloudstore.exception.AccessDeniedCustomException;
import com.cloudstore.repository.FileShareRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Single source of truth for "can this user touch this file" decisions.
 * Share-permission lookups are cached in Redis since they are read far more
 * often than they change; writes evict the specific cache entry immediately.
 */
@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final FileShareRepository fileShareRepository;

    public boolean isOwner(User user, FileEntity file) {
        return file.getOwner().getId().equals(user.getId());
    }

    @Cacheable(cacheNames = RedisConfig.SHARE_PERMISSION_CACHE, key = "#file.id + ':' + #user.id")
    public Optional<SharePermission> getEffectivePermission(User user, FileEntity file) {
        if (isOwner(user, file)) {
            return Optional.of(SharePermission.EDIT);
        }
        return fileShareRepository.findByFileAndSharedWith(file, user).map(FileShare::getPermission);
    }

    public void requireReadAccess(User user, FileEntity file) {
        if (getEffectivePermission(user, file).isEmpty()) {
            throw new AccessDeniedCustomException("You do not have access to this file");
        }
    }

    public void requireWriteAccess(User user, FileEntity file) {
        Optional<SharePermission> permission = getEffectivePermission(user, file);
        if (permission.isEmpty() || permission.get() == SharePermission.VIEW) {
            throw new AccessDeniedCustomException("You do not have edit access to this file");
        }
    }

    public void requireOwner(User user, FileEntity file) {
        if (!isOwner(user, file)) {
            throw new AccessDeniedCustomException("Only the owner can perform this action");
        }
    }

    @CacheEvict(cacheNames = RedisConfig.SHARE_PERMISSION_CACHE, key = "#file.id + ':' + #user.id")
    public void evictPermission(FileEntity file, User user) {
        // no-op body: annotation handles eviction
    }
}
