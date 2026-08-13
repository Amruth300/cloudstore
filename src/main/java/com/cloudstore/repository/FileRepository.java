package com.cloudstore.repository;

import com.cloudstore.entity.FileEntity;
import com.cloudstore.entity.Folder;
import com.cloudstore.entity.User;
import com.cloudstore.entity.enums.FileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileRepository extends JpaRepository<FileEntity, UUID> {

    Optional<FileEntity> findByIdAndOwner(UUID id, User owner);

    Optional<FileEntity> findByIdAndStatus(UUID id, FileStatus status);

    Page<FileEntity> findByOwnerAndFolderAndStatus(User owner, Folder folder, FileStatus status, Pageable pageable);

    Page<FileEntity> findByOwnerAndFolderIsNullAndStatus(User owner, FileStatus status, Pageable pageable);

    Page<FileEntity> findByOwnerAndStatus(User owner, FileStatus status, Pageable pageable);

    @Query("SELECT f FROM FileEntity f WHERE f.owner = :owner AND f.status = :status " +
            "AND (:name IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :name, '%'))) " +
            "AND (:contentType IS NULL OR f.contentType = :contentType)")
    Page<FileEntity> search(@Param("owner") User owner,
                             @Param("status") FileStatus status,
                             @Param("name") String name,
                             @Param("contentType") String contentType,
                             Pageable pageable);

    List<FileEntity> findByOwnerAndStatusAndTrashedAtBefore(User owner, FileStatus status, java.time.Instant before);

    List<FileEntity> findByStatusAndTrashedAtBefore(FileStatus status, java.time.Instant before);
}
