package com.lpn.aibi.llmorchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class AuthBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthBootstrap.class);

    private final AuthService authService;
    private final AuthProperties properties;

    AuthBootstrap(AuthService authService, AuthProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String password = properties.bootstrapAdmin().password();
        if (password == null || password.isBlank()) {
            LOGGER.warn("No bootstrap administrator password is configured; existing administrator accounts are unchanged");
            return;
        }
        authService.bootstrapAdmin(properties.bootstrapAdmin().username(), password);
        LOGGER.info("Bootstrap administrator credentials applied for user {}", properties.bootstrapAdmin().username());
    }
}
