# Java Services

Spring Boot services for the LPN AI-BI platform.

- `llm-orchestrator`: LLM coordination service, exposed through Docker on port `8081`.
- `sql-executor`: SQL execution service, exposed through Docker on port `8082`.

Build all modules:

```bash
./gradlew build
```
