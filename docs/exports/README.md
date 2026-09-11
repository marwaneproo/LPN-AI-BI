# Compiere Snapshot Exports

This directory defines how Youssef exports Compiere data from the company PC and transfers it into this project. The AI-BI application never connects to LPN's Oracle database at runtime. It only imports explicit snapshot bundles.

## Snapshot Bundle Format

A snapshot bundle is a zip file named:

```text
{name}-{YYYYMMDD}.zip
```

Example:

```text
lpn-core-business-20260429.zip
```

The zip must contain:

```text
lpn-core-business-20260429/
+-- manifest.json
+-- schema.sql
+-- csv/
    +-- C_ORDER.csv
    +-- C_ORDERLINE.csv
    +-- C_INVOICE.csv
    +-- ...
```

## File Requirements

- `manifest.json`: describes the snapshot, export timestamp, source system, table list, row counts, columns exported, and filters used.
- `schema.sql`: PostgreSQL `CREATE TABLE` statements matching the CSV files. Use PostgreSQL types, not Oracle types.
- `csv/*.csv`: one CSV per exported table, with a header row, UTF-8 encoding, comma delimiter, and quoted text fields when needed.

## Company PC Export Procedure

1. On the company PC, run one SQL export query per business table.
2. Save each result as CSV with a header row and UTF-8 encoding.
3. Export or manually write the matching PostgreSQL `schema.sql` using the type mapping rules in `schema.template.sql`.
4. Fill `manifest.json` using `manifest.template.json`.
5. Validate row counts and sample rows before transferring.
6. Zip the folder and transfer the zip to the development machine.
7. Place transferred bundles under `docs/exports/` temporarily while importing. Raw bundles are ignored by git.

## Validation Checklist

Before transferring a bundle, confirm:

- Row counts in `manifest.json` match the CSV files.
- Each CSV opens cleanly with `head -5` or Excel preview.
- Each CSV has exactly one header row.
- Important ID columns are present, especially primary keys and foreign keys.
- At least one row from each table can be cross-referenced in Compiere's UI by primary key.
- Date filters are documented in `manifest.json`.
- No private HR/salary/personal tables are included.

## Row Count Check

For a CSV with a header row:

```powershell
(Get-Content .\csv\C_ORDER.csv | Measure-Object -Line).Lines - 1
```

The result must match the `row_count` in `manifest.json`.

## Sample Inspection

```powershell
Get-Content .\csv\C_ORDER.csv -TotalCount 5
```

Look for broken delimiters, mojibake, missing headers, or shifted columns.

## Notes

- Prefer a filtered first snapshot for very large tables. For example, export transactional rows from 2021 onward first, then widen later if performance allows.
- Keep original Compiere table and column names for traceability.
- Do not commit raw CSV, XLSX, SQL dump, or zip exports.
