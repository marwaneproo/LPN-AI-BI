from typing import Literal

import sqlglot
from fastapi import FastAPI
from pydantic import BaseModel, Field
from sqlglot import exp
from sqlglot.errors import ParseError

app = FastAPI(title="LPN SQL Validator")

DEFAULT_LIMIT = 1000
SUPPORTED_DIALECTS = {"postgres"}

FORBIDDEN_EXPRESSION_TYPES: tuple[type[exp.Expression], ...] = (
    exp.Insert,
    exp.Update,
    exp.Delete,
    exp.Drop,
    exp.Alter,
    exp.TruncateTable,
    exp.Create,
    exp.Copy,
)
FORBIDDEN_EXPRESSION_NAMES = {
    exp.Insert: "INSERT",
    exp.Update: "UPDATE",
    exp.Delete: "DELETE",
    exp.Drop: "DROP",
    exp.Alter: "ALTER",
    exp.TruncateTable: "TRUNCATE",
    exp.Create: "CREATE",
    exp.Copy: "COPY",
}
FORBIDDEN_FUNCTIONS = {
    "copy_from",
    "copy_to",
    "lo_export",
    "lo_import",
    "pg_ls_dir",
    "pg_read_file",
    "pg_terminate_backend",
}


class ValidateSqlRequest(BaseModel):
    sql: str = Field(min_length=1)
    dialect: Literal["postgres"] = "postgres"


class ValidateSqlResponse(BaseModel):
    is_valid: bool
    is_safe: bool
    statement_count: int = 0
    first_statement_kind: str | None = None
    tables_referenced: list[str] = Field(default_factory=list)
    forbidden_constructs: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    rewritten_sql: str | None = None
    errors: list[str] = Field(default_factory=list)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/v1/validate", response_model=ValidateSqlResponse)
def validate_sql(request: ValidateSqlRequest) -> ValidateSqlResponse:
    try:
        statements = sqlglot.parse(request.sql, dialect=request.dialect)
    except ParseError as exception:
        return ValidateSqlResponse(
            is_valid=False,
            is_safe=False,
            errors=[_clean_error(str(exception))],
        )

    statements = [statement for statement in statements if statement is not None]
    if not statements:
        return ValidateSqlResponse(
            is_valid=False,
            is_safe=False,
            errors=["No SQL statement could be parsed."],
        )

    first_statement = statements[0]
    first_statement_kind = _statement_kind(first_statement)
    forbidden_constructs = _forbidden_constructs(statements)
    tables_referenced = _tables_referenced(statements)
    warnings: list[str] = []

    if len(statements) != 1:
        forbidden_constructs.append("MULTI_STATEMENT")

    if first_statement_kind != "SELECT":
        forbidden_constructs.append(first_statement_kind)

    forbidden_constructs = _dedupe(forbidden_constructs)
    is_safe = len(forbidden_constructs) == 0
    rewritten_sql = _rewritten_sql(first_statement, request.dialect, warnings) if is_safe else None

    return ValidateSqlResponse(
        is_valid=True,
        is_safe=is_safe,
        statement_count=len(statements),
        first_statement_kind=first_statement_kind,
        tables_referenced=tables_referenced,
        forbidden_constructs=forbidden_constructs,
        warnings=warnings,
        rewritten_sql=rewritten_sql,
    )


def _statement_kind(statement: exp.Expression) -> str:
    if isinstance(statement, exp.Select):
        return "SELECT"
    return statement.key.upper()


def _forbidden_constructs(statements: list[exp.Expression]) -> list[str]:
    constructs: list[str] = []
    for statement in statements:
        for forbidden_type in FORBIDDEN_EXPRESSION_TYPES:
            if isinstance(statement, forbidden_type) or any(statement.find_all(forbidden_type)):
                constructs.append(FORBIDDEN_EXPRESSION_NAMES[forbidden_type])

        for function in statement.find_all(exp.Func):
            function_name = (function.name or "").lower()
            if function_name in FORBIDDEN_FUNCTIONS:
                constructs.append(function_name)

    return _dedupe(constructs)


def _tables_referenced(statements: list[exp.Expression]) -> list[str]:
    cte_names = {
        cte.alias_or_name.lower()
        for statement in statements
        for cte in statement.find_all(exp.CTE)
        if cte.alias_or_name
    }
    tables: list[str] = []

    for statement in statements:
        for table in statement.find_all(exp.Table):
            table_name = table.name
            if not table_name or table_name.lower() in cte_names:
                continue

            schema = table.db or "business"
            tables.append(f"{schema}.{table_name}".lower())

    return _dedupe(tables)


def _rewritten_sql(statement: exp.Expression, dialect: str, warnings: list[str]) -> str:
    if statement.args.get("limit") is None:
        warnings.append(f"LIMIT {DEFAULT_LIMIT} added automatically.")
        statement = statement.copy().limit(DEFAULT_LIMIT)
    return statement.sql(dialect=dialect)


def _dedupe(values: list[str]) -> list[str]:
    seen: set[str] = set()
    deduped: list[str] = []
    for value in values:
        normalized = value.lower()
        if normalized not in seen:
            deduped.append(value)
            seen.add(normalized)
    return deduped


def _clean_error(error: str) -> str:
    return " ".join(error.split())
