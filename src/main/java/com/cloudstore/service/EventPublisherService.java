package com.cloudstore.service;

import com.cloudstore.config.KafkaConfig;
import com.cloudstore.event.FileDeletedEvent;
import com.cloudstore.event.FileSharedEvent;
import com.cloudstore.event.FileUploadedEvent;
import com.cloudstore.event.FileVersionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes domain events to Kafka asynchronously so request threads are never
 * blocked on downstream consumers (audit logging, search indexing, notifications, etc.).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishFileUploaded(FileUploadedEvent event) {
        send(KafkaConfig.FILE_UPLOADED, event.fileId().toString(), event);
    }

    public void publishFileDeleted(FileDeletedEvent event) {
        send(KafkaConfig.FILE_DELETED, event.fileId().toString(), event);
    }

    public void publishFileShared(FileSharedEvent event) {
        send(KafkaConfig.FILE_SHARED, event.fileId().toString(), event);
    }

    public void publishFileVersionCreated(FileVersionCreatedEvent event) {
        send(KafkaConfig.FILE_VERSION_CREATED, event.fileId().toString(), event);
    }

    private void send(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Failed to publish event to topic {}: {}", topic, ex.getMessage());
            }
        });
    }
}
