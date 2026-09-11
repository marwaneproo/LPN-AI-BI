.PHONY: python-sync python-test python-lint smoke

python-sync:
	cd services-python && python -m uv sync

python-test:
	cd services-python && python -m uv run pytest

python-lint:
	cd services-python && python -m uv run ruff check .

smoke:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke.ps1
