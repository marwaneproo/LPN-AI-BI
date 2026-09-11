from __future__ import annotations

import json
import zipfile
from collections.abc import Callable
from pathlib import Path

from click.testing import CliRunner

from data_import.cli import cli


FIXTURE_BUNDLE = Path(__file__).parent / "fixtures" / "sample-bundle.zip"


def test_import_dry_run_happy_path() -> None:
    result = CliRunner().invoke(cli, ["import", str(FIXTURE_BUNDLE), "--dry-run"])

    assert result.exit_code == 0
    assert "DRY RUN - no data written to PostgreSQL" in result.output
    assert "C_ORDER" in result.output
    assert "C_BPARTNER" in result.output
    assert "Total tables: 2" in result.output
    assert "Total rows: 20" in result.output


def test_import_dry_run_missing_csv(tmp_path: Path) -> None:
    broken_bundle = _rewrite_bundle(tmp_path, skip_names={"csv/C_ORDER.csv"})

    result = CliRunner().invoke(cli, ["import", str(broken_bundle), "--dry-run"])

    assert result.exit_code != 0
    assert "CSV file is missing" in result.output


def test_import_dry_run_row_count_mismatch(tmp_path: Path) -> None:
    def mutate_manifest(manifest: dict) -> dict:
        manifest["tables"][0]["row_count"] = 999
        return manifest

    broken_bundle = _rewrite_bundle(tmp_path, mutate_manifest=mutate_manifest)

    result = CliRunner().invoke(cli, ["import", str(broken_bundle), "--dry-run"])

    assert result.exit_code != 0
    assert "Row count mismatch for C_ORDER" in result.output


def test_import_dry_run_malformed_json(tmp_path: Path) -> None:
    broken_bundle = _rewrite_bundle(tmp_path, replace_files={"manifest.json": "{not-json"})

    result = CliRunner().invoke(cli, ["import", str(broken_bundle), "--dry-run"])

    assert result.exit_code != 0
    assert "manifest.json is malformed" in result.output


def _rewrite_bundle(
    tmp_path: Path,
    *,
    skip_names: set[str] | None = None,
    replace_files: dict[str, str] | None = None,
    mutate_manifest: Callable[[dict], dict] | None = None,
) -> Path:
    skip_names = skip_names or set()
    replace_files = replace_files or {}
    target = tmp_path / "bundle.zip"

    with zipfile.ZipFile(FIXTURE_BUNDLE) as source, zipfile.ZipFile(target, "w") as dest:
        for name in source.namelist():
            if name in skip_names:
                continue

            if name in replace_files:
                data = replace_files[name].encode("utf-8")
            elif name == "manifest.json" and mutate_manifest:
                manifest = json.loads(source.read(name).decode("utf-8"))
                data = json.dumps(mutate_manifest(manifest), indent=2).encode("utf-8")
            else:
                data = source.read(name)

            dest.writestr(name, data)

    return target
