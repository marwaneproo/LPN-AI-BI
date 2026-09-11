from fastapi import FastAPI

app = FastAPI(title="LPN Data Import")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
