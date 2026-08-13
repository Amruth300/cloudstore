package com.cloudstore.event;

import java.time.Instant;
import java.util.UUID;

public record FileVersionCreatedEvent(UUID fileId, UUID versionId, int versionNumber, UUID createdByUserId, Instant occurredAt) {
}
