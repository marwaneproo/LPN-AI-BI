package com.lpn.aibi.llmorchestrator;

import java.time.Instant;
import java.util.UUID;

record AuthUser(
        UUID id,
        String username,
        String fullName,
        String role,
        String status,
        Instant createdAt,
        Instant decidedAt,
        String decidedBy,
        Instant lastLoginAt) {
}
