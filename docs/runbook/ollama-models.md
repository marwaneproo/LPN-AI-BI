# Ollama Models Runbook

## Task 1.3 Commands

Run from the repository root after `docker compose up -d`.

```powershell
docker exec lpn-ai-bi-ollama-1 ollama pull llama3.2:3b
docker exec lpn-ai-bi-ollama-1 ollama pull a-kore/Arctic-Text2SQL-R1-7B
docker exec lpn-ai-bi-ollama-1 ollama list
```

Optional fallback model from the production plan, not pulled during this blocked run:

```powershell
docker exec lpn-ai-bi-ollama-1 ollama pull qwen2.5-coder:7b-instruct
```

## Current Model List

```text
NAME                                   ID              SIZE      MODIFIED
a-kore/Arctic-Text2SQL-R1-7B:latest    a55cdf92028b    8.1 GB    pulled 2026-04-29
llama3.2:3b                            a80c4f17acd5    2.0 GB    pulled 2026-04-29
```

## Smoke Tests

### Llama 3.2 3B

Command:

```powershell
curl.exe -s http://localhost:11434/api/generate `
  -H "Content-Type: application/json" `
  -d '{"model":"llama3.2:3b","prompt":"Reply with exactly: OK","stream":false}'
```

Result:

- Passed.
- Response contained `"response":"OK"`.
- First cold load took about 68.9 seconds.
- Ollama reported `load_duration` around 67.8 seconds.

### Arctic Text2SQL

Command:

```powershell
curl.exe -s http://localhost:11434/api/generate `
  -H "Content-Type: application/json" `
  -d '{"model":"a-kore/Arctic-Text2SQL-R1-7B","prompt":"You are a SQL generator. Schema: table users(id, name). Question: how many users? Output only SQL.","stream":false}'
```

Initial result:

- Failed.
- Response: `{"error":"model requires more system memory (7.9 GiB) than is available (7.4 GiB)"}`.
- Cause: Docker Desktop was using the WSL2 backend, and WSL exposed too little memory to Docker.

Resolution:

- Created `%USERPROFILE%\.wslconfig`:

```ini
[wsl2]
memory=12GB
processors=8
swap=8GB
```

- Ran `wsl --shutdown`.
- Restarted Docker Desktop.
- Confirmed `docker stats --no-stream lpn-ai-bi-ollama-1` showed about `11.69GiB` memory limit.

Retest result:

- Passed.
- Response included valid SQL:

```sql
SELECT COUNT(*) AS total_users FROM users;
```
- Cold load was slow: about 197.8 seconds total, with about 134.6 seconds load duration.

## Diagnosis

`docker logs lpn-ai-bi-ollama-1` reported:

```text
Load failed ... error="llama runner process has terminated: %!w(<nil>)"
```

`docker stats --no-stream lpn-ai-bi-ollama-1` showed the Ollama container has a memory limit of about `7.623 GiB`.

`docker logs lpn-ai-bi-ollama-1` showed Ollama is using CPU, not GPU:

```text
system memory total="7.6 GiB" free="6.5 GiB"
model weights device=CPU size="1.9 GiB"  # for llama3.2:3b
```

`nvidia-smi` showed the RTX 4060 is present on the Windows host but no GPU memory is used by Ollama.

## Previous Blocker

Arctic originally could not load because Docker exposed only about `7.4 GiB` available memory. The WSL2 memory configuration above resolved this.

## Suggested Fixes

1. GPU acceleration is still not active; `nvidia-smi` shows 0 MiB used by Ollama.
2. Arctic can run on CPU with the 12 GB WSL memory limit, but cold starts are slow.
3. If latency becomes unacceptable, enable NVIDIA GPU passthrough for Docker Desktop / WSL2 and adapt the GPU reservation section in `docker-compose.yml`.
