package com.cloudstore.entity;

import com.cloudstore.entity.enums.FileStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "files", indexes = {
        @Index(name = "idx_files_owner", columnList = "owner_id"),
        @Index(name = "idx_files_folder", columnList = "folder_id"),
        @Index(name = "idx_files_status", columnList = "status"),
        @Index(name = "idx_files_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileEntity extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 150)
    private String contentType;

    @Column(nullable = false)
    private Long sizeBytes;

    /** Key of the CURRENT version's object in GCS bucket. Mirrors latest FileVersion.storageKey. */
    @Column(nullable = false, length = 500)
    private String storageKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FileStatus status = FileStatus.ACTIVE;

    @Column(name = "trashed_at")
    private Instant trashedAt;

    @Column(name = "current_version", nullable = false)
    @Builder.Default
    private Integer currentVersion = 1;
}
