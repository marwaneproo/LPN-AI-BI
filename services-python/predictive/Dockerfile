FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY services-python/predictive /tmp/predictive

RUN pip install --no-cache-dir /tmp/predictive \
    && rm -rf /tmp/predictive

EXPOSE 8000

CMD ["uvicorn", "predictive.main:app", "--host", "0.0.0.0", "--port", "8000"]
