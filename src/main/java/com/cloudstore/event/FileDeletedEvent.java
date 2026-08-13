package com.cloudstore.event;

import java.time.Instant;
import java.util.UUID;

public record FileDeletedEvent(UUID fileId, UUID ownerId, String fileName, boolean permanent, Instant occurredAt) {
}
