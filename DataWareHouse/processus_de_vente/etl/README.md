# Processus de Vente ETL

This folder contains the repeatable ETL pipeline for the sales-process data warehouse prototype.

## Purpose

The ETL converts the current ERP Excel/CSV extracts into clean warehouse-ready files:

- `output/staging/stg_*.csv`: normalized source copies.
- `output/dimensions/dim_*.csv`: business dimensions.
- `output/facts/fact_*.csv`: measurable sales-process facts.
- `output/marts/mart_*.csv`: Power BI and frontend-ready reporting tables.
- `validation/etl_validation_report.md`: row-count, total, and key-coverage checks.

## Run

From the repository root:

```powershell
python DataWareHouse\processus_de_vente\etl\scripts\run_etl.py
```

If your shell does not resolve `python`, use the bundled workspace Python or your local Python environment with `pandas` and `openpyxl` installed.

## Source Scope

The ETL uses only the currently available project data:

- `Youssef_Extractions/vente_clean_import/*.csv`
- selected enrichment exports from `Youssef_Extractions/3rd_Extraction`
- selected enrichment exports from `Youssef_Extractions/4th_Extraction`

Future wider trend reporting should rerun the same pipeline after exporting a 24-month source window.

## Important Assumptions

- Dimensions are maintained as Type 1 for this prototype. Current business labels are kept; historical label versions are not tracked yet.
- Unknown or missing dimension references use key `0`.
- Supplier analytics uses one primary supplier per product, selected from `M_PRODUCT_PO` using current/active vendor indicators where available.
- Power BI should connect first to `mart_*` outputs for simple reporting, then to `dim_*` and `fact_*` when deeper analysis is needed.

