package com.cloudstore.repository;

import com.cloudstore.entity.FileEntity;
import com.cloudstore.entity.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {

    List<FileVersion> findByFileOrderByVersionNumberDesc(FileEntity file);

    Optional<FileVersion> findByFileAndVersionNumber(FileEntity file, Integer versionNumber);

    Optional<FileVersion> findTopByFileOrderByVersionNumberDesc(FileEntity file);
}
