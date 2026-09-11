from __future__ import annotations

import json
import urllib.error
import urllib.request


ENDPOINT = "http://localhost:8084/v1/retrieve"

CASES = [
    ("How many orders did we have last month?", {"C_ORDER"}),
    ("Which customers bought products?", {"C_BPARTNER", "C_ORDER"}),
    ("Total invoice amount by customer", {"C_INVOICE", "C_BPARTNER"}),
    ("Top products by ordered quantity", {"C_ORDERLINE", "M_PRODUCT"}),
    ("Which products risk stock rupture soon?", {"RV_STORAGE", "M_PRODUCT"}),
    ("Compare delivered quantity and invoiced quantity by order line", {"C_ORDERLINE", "M_INOUTLINE", "C_INVOICELINE"}),
]


def retrieve(question: str) -> list[dict[str, object]]:
    body = json.dumps({"question": question, "top_k": 8, "language": "en"}).encode("utf-8")
    request = urllib.request.Request(
        ENDPOINT,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> int:
    failures: list[str] = []
    for question, expected_tables in CASES:
        try:
            results = retrieve(question)
        except (urllib.error.URLError, TimeoutError) as exc:
            failures.append(f"{question}: request failed: {exc}")
            continue

        table_names = [str(result["table_name"]) for result in results[:12]]
        status = "PASS" if expected_tables <= set(table_names) else "FAIL"
        print(f"[{status}] {question}")
        print(f"  expected: {', '.join(sorted(expected_tables))}")
        print(f"  returned: {', '.join(table_names)}")
        if status == "FAIL":
            failures.append(question)

    if failures:
        print("\nSchema RAG live verification failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("\nSchema RAG live verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
