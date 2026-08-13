package com.cloudstore.entity;

import com.cloudstore.entity.enums.SharePermission;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "file_shares", indexes = {
        @Index(name = "idx_shares_file", columnList = "file_id"),
        @Index(name = "idx_shares_user", columnList = "shared_with_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_file_shared_with", columnNames = {"file_id", "shared_with_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileShare extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_with_id", nullable = false)
    private User sharedWith;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_by_id", nullable = false)
    private User sharedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SharePermission permission;
}
