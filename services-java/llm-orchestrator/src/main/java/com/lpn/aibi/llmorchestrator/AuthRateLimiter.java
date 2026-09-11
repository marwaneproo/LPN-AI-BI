package com.lpn.aibi.llmorchestrator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class AuthRateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    AuthRateLimiter() {
        this(Clock.systemUTC());
    }

    AuthRateLimiter(Clock clock) {
        this.clock = clock;
    }

    void check(String key, int maximumAttempts, Duration window) {
        Instant now = clock.instant();
        Deque<Instant> timestamps = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            Instant cutoff = now.minus(window);
            while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(cutoff)) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= maximumAttempts) {
                long retryAfter = Math.max(1, Duration.between(now, timestamps.peekFirst().plus(window)).toSeconds());
                throw new RateLimitExceededException(retryAfter);
            }
            timestamps.addLast(now);
        }
    }

    void reset(String key) {
        attempts.remove(key);
    }
}
