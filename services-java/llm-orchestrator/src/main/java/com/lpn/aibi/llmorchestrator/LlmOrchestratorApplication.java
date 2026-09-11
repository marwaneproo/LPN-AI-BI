package com.lpn.aibi.llmorchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
public class LlmOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmOrchestratorApplication.class, args);
    }
}
