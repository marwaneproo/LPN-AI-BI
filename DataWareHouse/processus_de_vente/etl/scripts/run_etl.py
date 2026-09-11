from __future__ import annotations

import json
import time
from datetime import datetime, timezone

from extract_sources import load_sources, project_path, write_staging
from transform_dimensions import build_dimensions, write_dimensions
from transform_facts import build_facts, build_marts, write_outputs
from validate_etl import validate, write_report


LOG_PATH = project_path("DataWareHouse/processus_de_vente/etl/logs/etl_run_log.json")


def main() -> None:
    started = time.perf_counter()
    run_log = {
        "started_at": datetime.now(timezone.utc).isoformat(),
        "steps": [],
    }

    sources = load_sources()
    write_staging(sources)
    run_log["steps"].append({"step": "staging", "tables": len(sources)})

    dimensions = build_dimensions(sources)
    write_dimensions(dimensions)
    run_log["steps"].append({"step": "dimensions", "tables": len(dimensions)})

    facts = build_facts(sources, dimensions)
    run_log["steps"].append({"step": "facts", "tables": len(facts)})

    marts = build_marts(facts, dimensions)
    write_outputs(facts, marts)
    run_log["steps"].append({"step": "marts", "tables": len(marts)})

    report = validate()
    write_report(report)
    run_log["steps"].append({"step": "validation", "status": report["overall_status"]})

    run_log["finished_at"] = datetime.now(timezone.utc).isoformat()
    run_log["duration_seconds"] = round(time.perf_counter() - started, 2)
    run_log["overall_status"] = report["overall_status"]
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    LOG_PATH.write_text(json.dumps(run_log, indent=2, ensure_ascii=False), encoding="utf-8")

    print(json.dumps(run_log, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
