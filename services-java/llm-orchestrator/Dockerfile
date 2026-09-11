FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY services-java ./services-java
WORKDIR /workspace/services-java
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew :llm-orchestrator:bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/services-java/llm-orchestrator/build/libs/*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
