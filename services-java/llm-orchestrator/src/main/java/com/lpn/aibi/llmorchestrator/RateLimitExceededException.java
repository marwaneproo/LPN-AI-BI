package com.lpn.aibi.llmorchestrator;

final class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    RateLimitExceededException(long retryAfterSeconds) {
        super("Too many authentication attempts");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
