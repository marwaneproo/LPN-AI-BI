# Schema Metadata CSV

`docs/schema_metadata.csv` is the curated business dictionary used by the Schema Retrieval service. It gives the AI human-readable context for Compiere tables and columns without renaming the source schema.

## Columns

| Column | Required | Meaning |
|---|---:|---|
| `table_name` | Yes | Original Compiere table name in uppercase, for example `C_ORDER`. |
| `module` | Yes | Business domain: `Sales`, `Purchasing`, `Inventory`, `Finance`, `HR`, or `Reference`. |
| `description_en` | Yes | One concise English sentence describing what the table stores. |
| `description_fr` | No | French description. Leave empty during Week 1; fill during French language work. |
| `key_columns` | Yes | Pipe-delimited list of `column_name:type:description_en` triples. |
| `relations` | No | Pipe-delimited list of `COLUMN -> OTHER_TABLE (OTHER_COLUMN)` relationships. |
| `sensitive` | Yes | `true` if the table must be excluded from Schema RAG retrieval; otherwise `false`. |
| `notes` | No | Caveats, business rules, filters, or usage hints. |

## Formatting Rules

- Keep table and column names exactly traceable to Compiere.
- Use `|` between entries inside `key_columns` and `relations`.
- Avoid commas inside fields unless the field is quoted, because this is a CSV file.
- Use PostgreSQL-ish type names in `key_columns` after import mapping, such as `bigint`, `varchar`, `timestamp`, `numeric`, and `char(1)`.
- Set `sensitive` to `true` for HR, salary, personal identity, or private-contact tables that should never be retrieved by the AI.
- Prefer clear business language over ERP jargon in descriptions.

## Full Example

```csv
table_name,module,description_en,description_fr,key_columns,relations,sensitive,notes
C_ORDER,Sales,Sales order header storing order dates status totals customer and document type.,,C_ORDER_ID:bigint:Primary order identifier|DOCUMENTNO:varchar:Human-readable order number|C_BPARTNER_ID:bigint:Customer or supplier business partner|DATEORDERED:timestamp:Order date,C_BPARTNER_ID -> C_BPARTNER (C_BPARTNER_ID)|C_ORDER_ID -> C_ORDERLINE (C_ORDER_ID),false,Use ISSOTRX = Y for customer sales orders.
```

## Starter Rows

Task 1.5 seeds five rows:

- `C_ORDER`
- `C_ORDERLINE`
- `C_INVOICE`
- `C_BPARTNER`
- `M_PRODUCT`

Youssef should extend this file with the remaining high-value Compiere business tables before Week 2 starts.

