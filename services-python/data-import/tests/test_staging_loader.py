from __future__ import annotations

import uuid

import pytest

from data_import.staging_loader import loader as loader_mod
from data_import.staging_loader.loader import (
    StagingLoader,
    StructuralError,
    iter_batches,
)
from data_import.staging_loader.source_map import SourceSpec


# --------------------------------------------------------------------------
# Fakes (mock DB — no PostgreSQL involved)
# --------------------------------------------------------------------------
class FakeCursor:
    def __init__(self, store: dict) -> None:
        self.store = store

    def __enter__(self) -> "FakeCursor":
        return self

    def __exit__(self, *exc) -> bool:
        return False

    def execute(self, sql, params=None) -> None:
        self.store["executed"].append((" ".join(str(sql).split()), params))


class FakeConn:
    def __init__(self) -> None:
        self.store = {"executed": [], "commits": 0, "rollbacks": 0}

    def cursor(self) -> FakeCursor:
        return FakeCursor(self.store)

    def commit(self) -> None:
        self.store["commits"] += 1

    def rollback(self) -> None:
        self.store["rollbacks"] += 1

    def close(self) -> None:
        pass


SPEC = SourceSpec(
    staging_table="stg_c_region",
    natural_key=("c_region_id",),
    xlsx_2024="2024_13_C_REGION.xlsx",
    csv_2025="C_REGION.csv",
    chunked=False,
    exclude_xlsx=False,
)

CHUNKED_SPEC = SourceSpec(
    staging_table="stg_c_orderline",
    natural_key=("c_orderline_id",),
    xlsx_2024="2024_02_C_ORDERLINE.xlsx",
    csv_2025="C_ORDERLINE.csv",
    chunked=True,
    exclude_xlsx=False,
)


@pytest.fixture()
def captured_batches(monkeypatch):
    """Capture every execute_values call's batch (list of tuples)."""

    batches: list[list[tuple]] = []

    def fake_execute_values(cur, sql, argslist, page_size=None):
        batches.append(list(argslist))

    monkeypatch.setattr(loader_mod, "execute_values", fake_execute_values)
    return batches


def _fix_columns(monkeypatch, columns):
    monkeypatch.setattr(
        loader_mod, "fetch_table_columns", lambda conn, schema, table: columns
    )


# --------------------------------------------------------------------------
# iter_batches
# --------------------------------------------------------------------------
def test_iter_batches_splits_12001_into_5000_5000_2001() -> None:
    sizes = [len(b) for b in iter_batches(range(12001), 5000)]
    assert sizes == [5000, 5000, 2001]


def test_iter_batches_empty() -> None:
    assert list(iter_batches([], 5000)) == []


# --------------------------------------------------------------------------
# Batching through the loader (the [5000, 5000, 2001] proof)
# --------------------------------------------------------------------------
def test_loader_flushes_in_5000_row_batches(monkeypatch, captured_batches) -> None:
    _fix_columns(monkeypatch, ["c_orderline_id", "_source_file", "_source_tag", "_etl_run_id"])
    conn = FakeConn()
    run_id = uuid.uuid4()
    loader = StagingLoader(conn, run_id)

    rows = ({"c_orderline_id": i} for i in range(12001))
    result = loader.load_rows(CHUNKED_SPEC, "2024_xlsx", "2024_02_C_ORDERLINE.xlsx", rows)

    assert [len(b) for b in captured_batches] == [5000, 5000, 2001]
    assert result.rows_read == 12001
    assert result.rows_inserted == 12001
    assert result.rows_rejected == 0


# --------------------------------------------------------------------------
# Metadata stamping
# --------------------------------------------------------------------------
def test_metadata_stamped_on_every_row(monkeypatch, captured_batches) -> None:
    _fix_columns(monkeypatch, ["c_region_id", "name", "_source_file", "_source_tag", "_etl_run_id"])
    conn = FakeConn()
    run_id = uuid.uuid4()
    loader = StagingLoader(conn, run_id)

    rows = [
        {"c_region_id": 1, "name": "Souss"},
        {"c_region_id": 2, "name": "Casa"},
    ]
    loader.load_rows(SPEC, "2024_xlsx", "2024_13_C_REGION.xlsx", rows)

    inserted = [t for batch in captured_batches for t in batch]
    assert len(inserted) == 2
    for tup in inserted:
        # data cols (c_region_id, name) then (_source_file, _source_tag, _etl_run_id)
        assert tup[-3:] == ("2024_13_C_REGION.xlsx", "2024_xlsx", str(run_id))
    # data values preserved and column-ordered
    assert inserted[0][:2] == (1, "Souss")


def test_missing_source_column_inserted_as_none(monkeypatch, captured_batches) -> None:
    # 'description' exists in the table but not in the source row -> NULL
    _fix_columns(
        monkeypatch,
        ["c_region_id", "name", "description", "_source_file", "_source_tag", "_etl_run_id"],
    )
    conn = FakeConn()
    loader = StagingLoader(conn, uuid.uuid4())
    loader.load_rows(SPEC, "2024_xlsx", "f.xlsx", [{"c_region_id": 1, "name": "Souss"}])

    tup = captured_batches[0][0]
    assert tup[:3] == (1, "Souss", None)


# --------------------------------------------------------------------------
# Reject capture
# --------------------------------------------------------------------------
def test_null_natural_key_routed_to_rejects(monkeypatch, captured_batches) -> None:
    _fix_columns(monkeypatch, ["c_region_id", "name", "_source_file", "_source_tag", "_etl_run_id"])
    conn = FakeConn()
    loader = StagingLoader(conn, uuid.uuid4())

    rows = [
        {"c_region_id": 1, "name": "ok"},
        {"c_region_id": None, "name": "bad"},
        {"c_region_id": 3, "name": "ok2"},
    ]
    result = loader.load_rows(SPEC, "2024_xlsx", "f.xlsx", rows)

    assert result.rows_read == 3
    assert result.rows_inserted == 2
    assert result.rows_rejected == 1

    inserted = [t for batch in captured_batches for t in batch]
    assert {t[0] for t in inserted} == {1, 3}

    reject_inserts = [
        sql for sql, _ in conn.store["executed"] if "etl.stg_rejects" in sql
    ]
    assert len(reject_inserts) == 1


# --------------------------------------------------------------------------
# Idempotency: DELETE-per-_source_tag before any insert
# --------------------------------------------------------------------------
def test_delete_per_source_tag_runs_before_insert(monkeypatch, captured_batches) -> None:
    _fix_columns(monkeypatch, ["c_region_id", "_source_file", "_source_tag", "_etl_run_id"])
    conn = FakeConn()
    loader = StagingLoader(conn, uuid.uuid4())
    loader.load_rows(SPEC, "2024_xlsx", "f.xlsx", [{"c_region_id": 1}])

    statements = [sql for sql, _ in conn.store["executed"]]
    delete_idx = next(
        i for i, s in enumerate(statements) if s.startswith("DELETE FROM staging.")
    )
    delete_sql, delete_params = conn.store["executed"][delete_idx]
    assert "WHERE _source_tag = %s" in delete_sql
    assert delete_params == ("2024_xlsx",)


def test_idempotent_rerun_deletes_each_time(monkeypatch, captured_batches) -> None:
    _fix_columns(monkeypatch, ["c_region_id", "_source_file", "_source_tag", "_etl_run_id"])
    rows = [{"c_region_id": i} for i in range(10)]

    counts = []
    for _ in range(2):
        conn = FakeConn()
        loader = StagingLoader(conn, uuid.uuid4())
        result = loader.load_rows(SPEC, "2024_xlsx", "f.xlsx", list(rows))
        deletes = [s for s, _ in conn.store["executed"] if s.startswith("DELETE FROM staging.")]
        assert len(deletes) == 1  # every run wipes its tag first
        counts.append(result.rows_inserted)

    assert counts == [10, 10]  # stable across re-runs


# --------------------------------------------------------------------------
# Structural failures (fail loudly, no silent partial load)
# --------------------------------------------------------------------------
def test_missing_target_table_raises_structural(monkeypatch) -> None:
    _fix_columns(monkeypatch, [])  # table absent
    conn = FakeConn()
    loader = StagingLoader(conn, uuid.uuid4())
    with pytest.raises(StructuralError, match="not found"):
        loader.load_rows(SPEC, "2024_xlsx", "f.xlsx", [{"c_region_id": 1}])


def test_header_mismatch_raises_structural_before_mutation(monkeypatch, captured_batches) -> None:
    _fix_columns(monkeypatch, ["c_region_id", "_source_file", "_source_tag", "_etl_run_id"])
    conn = FakeConn()
    loader = StagingLoader(conn, uuid.uuid4())
    # source rows lack the natural-key column entirely
    with pytest.raises(StructuralError, match="header mismatch"):
        loader.load_rows(SPEC, "2024_xlsx", "wrong.xlsx", [{"name": "x"}])

    assert captured_batches == []  # nothing inserted
    assert not any(s.startswith("DELETE FROM staging.") for s, _ in conn.store["executed"])


def test_resolve_refuses_excluded_xlsx(monkeypatch) -> None:
    excluded = SourceSpec(
        staging_table="stg_m_product",
        natural_key=("m_product_id",),
        xlsx_2024=None,
        csv_2025="M_PRODUCT.csv",
        chunked=False,
        exclude_xlsx=True,
    )
    loader = StagingLoader(FakeConn(), uuid.uuid4())
    with pytest.raises(StructuralError, match="no 2024 xlsx source"):
        loader._resolve(excluded, "2024_xlsx")
