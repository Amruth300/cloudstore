package com.cloudstore.repository;

import com.cloudstore.entity.Folder;
import com.cloudstore.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    List<Folder> findByOwnerAndParentIsNullAndDeletedFalse(User owner);

    List<Folder> findByOwnerAndParentIdAndDeletedFalse(User owner, UUID parentId);

    Optional<Folder> findByIdAndOwner(UUID id, User owner);

    boolean existsByNameAndParentAndOwnerAndDeletedFalse(String name, Folder parent, User owner);

    boolean existsByNameAndParentIsNullAndOwnerAndDeletedFalse(String name, User owner);
}
