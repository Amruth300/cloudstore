package com.cloudstore.listener;

import com.cloudstore.config.KafkaConfig;
import com.cloudstore.event.FileDeletedEvent;
import com.cloudstore.event.FileSharedEvent;
import com.cloudstore.event.FileUploadedEvent;
import com.cloudstore.event.FileVersionCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Demonstrates asynchronous, decoupled event processing: a lightweight audit trail
 * that reacts to domain events without the originating request having to wait for it.
 * In a real deployment this could fan out to a search indexer, notification service,
 * or analytics pipeline without touching the core request path.
 */
@Component
@Slf4j
public class FileEventAuditListener {

    @KafkaListener(topics = KafkaConfig.FILE_UPLOADED, groupId = "cloudstore-audit")
    public void onFileUploaded(FileUploadedEvent event) {
        log.info("AUDIT: file uploaded fileId={} owner={} name={} size={}",
                event.fileId(), event.ownerId(), event.fileName(), event.sizeBytes());
    }

    @KafkaListener(topics = KafkaConfig.FILE_DELETED, groupId = "cloudstore-audit")
    public void onFileDeleted(FileDeletedEvent event) {
        log.info("AUDIT: file deleted fileId={} owner={} permanent={}",
                event.fileId(), event.ownerId(), event.permanent());
    }

    @KafkaListener(topics = KafkaConfig.FILE_SHARED, groupId = "cloudstore-audit")
    public void onFileShared(FileSharedEvent event) {
        log.info("AUDIT: file shared fileId={} sharedBy={} sharedWith={} permission={}",
                event.fileId(), event.sharedByUserId(), event.sharedWithUserId(), event.permission());
    }

    @KafkaListener(topics = KafkaConfig.FILE_VERSION_CREATED, groupId = "cloudstore-audit")
    public void onFileVersionCreated(FileVersionCreatedEvent event) {
        log.info("AUDIT: new version created fileId={} version={} createdBy={}",
                event.fileId(), event.versionNumber(), event.createdByUserId());
    }
}
