package com.cloudstore.repository;

import com.cloudstore.entity.FileEntity;
import com.cloudstore.entity.FileShare;
import com.cloudstore.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileShareRepository extends JpaRepository<FileShare, UUID> {

    List<FileShare> findByFile(FileEntity file);

    Optional<FileShare> findByFileAndSharedWith(FileEntity file, User sharedWith);

    List<FileShare> findBySharedWith(User sharedWith);

    void deleteByFileAndSharedWith(FileEntity file, User sharedWith);
}
