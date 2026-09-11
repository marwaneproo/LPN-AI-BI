dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("dev.langchain4j:langchain4j:1.14.0")
    implementation("dev.langchain4j:langchain4j-ollama-spring-boot-starter:1.14.0-beta24") {
        exclude(group = "org.springframework.boot")
    }
    runtimeOnly("org.postgresql:postgresql")
}

tasks.bootJar {
    mainClass.set("com.lpn.aibi.llmorchestrator.LlmOrchestratorApplication")
}
