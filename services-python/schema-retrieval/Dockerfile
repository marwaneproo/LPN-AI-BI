FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY services-python/schema-retrieval /tmp/schema-retrieval

RUN pip install --no-cache-dir /tmp/schema-retrieval \
    && rm -rf /tmp/schema-retrieval

EXPOSE 8084

CMD ["uvicorn", "schema_retrieval.main:app", "--host", "0.0.0.0", "--port", "8084"]
