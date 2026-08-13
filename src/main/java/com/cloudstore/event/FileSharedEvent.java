package com.cloudstore.event;

import java.time.Instant;
import java.util.UUID;

public record FileSharedEvent(UUID fileId, UUID sharedByUserId, UUID sharedWithUserId, String permission, Instant occurredAt) {
}
