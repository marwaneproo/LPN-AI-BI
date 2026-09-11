FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY services-python/sql-validator /tmp/sql-validator

RUN pip install --no-cache-dir /tmp/sql-validator \
    && rm -rf /tmp/sql-validator

EXPOSE 8086

CMD ["uvicorn", "sql_validator.main:app", "--host", "0.0.0.0", "--port", "8086"]
