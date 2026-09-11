package com.lpn.aibi.llmorchestrator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthRateLimiterTest {

    @Test
    void blocksAttemptsAtTheConfiguredLimitAndCanResetAfterSuccess() {
        AuthRateLimiter limiter = new AuthRateLimiter(Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC));

        limiter.check("login:alice", 2, Duration.ofMinutes(15));
        limiter.check("login:alice", 2, Duration.ofMinutes(15));
        assertThatThrownBy(() -> limiter.check("login:alice", 2, Duration.ofMinutes(15)))
                .isInstanceOf(RateLimitExceededException.class);

        limiter.reset("login:alice");
        limiter.check("login:alice", 2, Duration.ofMinutes(15));
    }
}
