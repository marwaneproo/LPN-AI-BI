from fastapi.testclient import TestClient

from sql_validator.main import app

client = TestClient(app)


def validate(sql: str) -> dict:
    response = client.post("/v1/validate", json={"sql": sql, "dialect": "postgres"})
    assert response.status_code == 200
    return response.json()


def test_health() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_happy_path_select_adds_limit_and_extracts_tables() -> None:
    result = validate("SELECT id, name FROM users")

    assert result["is_valid"] is True
    assert result["is_safe"] is True
    assert result["statement_count"] == 1
    assert result["first_statement_kind"] == "SELECT"
    assert result["tables_referenced"] == ["business.users"]
    assert result["forbidden_constructs"] == []
    assert result["rewritten_sql"].endswith("LIMIT 1000")


def test_existing_limit_is_preserved() -> None:
    result = validate("SELECT * FROM business.c_order LIMIT 25")

    assert result["is_safe"] is True
    assert result["tables_referenced"] == ["business.c_order"]
    assert result["rewritten_sql"] == "SELECT * FROM business.c_order LIMIT 25"
    assert result["warnings"] == []


def test_multi_statement_is_not_safe() -> None:
    result = validate("SELECT 1; DROP TABLE business.c_order")

    assert result["is_valid"] is True
    assert result["is_safe"] is False
    assert result["statement_count"] == 2
    assert "MULTI_STATEMENT" in result["forbidden_constructs"]
    assert "DROP" in result["forbidden_constructs"]
    assert result["rewritten_sql"] is None


def test_hidden_dml_in_comment_is_safe() -> None:
    result = validate("SELECT 1 -- DROP TABLE business.c_order")

    assert result["is_valid"] is True
    assert result["is_safe"] is True
    assert result["statement_count"] == 1
    assert result["forbidden_constructs"] == []


def test_delete_is_not_safe() -> None:
    result = validate("DELETE FROM foo")

    assert result["is_valid"] is True
    assert result["is_safe"] is False
    assert result["first_statement_kind"] == "DELETE"
    assert "DELETE" in result["forbidden_constructs"]


def test_forbidden_function_is_not_safe() -> None:
    result = validate("SELECT pg_read_file('/etc/passwd')")

    assert result["is_valid"] is True
    assert result["is_safe"] is False
    assert "pg_read_file" in result["forbidden_constructs"]


def test_cte_names_are_not_reported_as_base_tables() -> None:
    result = validate(
        """
        WITH recent_orders AS (
          SELECT c_order_id FROM c_order
        )
        SELECT COUNT(*) FROM recent_orders
        """
    )

    assert result["is_safe"] is True
    assert result["tables_referenced"] == ["business.c_order"]
