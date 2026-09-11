package com.lpn.aibi.llmorchestrator;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth")
record AuthProperties(
        boolean apiProtectionEnabled,
        Cookie cookie,
        Session session,
        Password password,
        BootstrapAdmin bootstrapAdmin,
        RateLimit rateLimit) {

    record Cookie(String name, boolean secure, String sameSite) {
    }

    record Session(Duration standardTtl, Duration rememberedTtl) {
    }

    record Password(int bcryptStrength) {
    }

    record BootstrapAdmin(String username, String password) {
    }

    record RateLimit(int loginAttempts, Duration loginWindow, int signupAttempts, Duration signupWindow) {
    }
}
