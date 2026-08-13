package com.cloudstore.event;

import java.time.Instant;
import java.util.UUID;

public record FileUploadedEvent(UUID fileId, UUID ownerId, String fileName, long sizeBytes, Instant occurredAt) {
}
