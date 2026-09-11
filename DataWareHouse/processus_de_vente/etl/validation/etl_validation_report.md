# ETL Validation Report

Generated at: `2026-06-01T11:02:37.547946+00:00`

Overall status: **PASS**

## Row Checks

| Fact | Source | Source rows | Fact rows | Status |
|---|---|---:|---:|---|
| fact_sales_order | c_order | 3350 | 3350 | PASS |
| fact_sales_order_line | c_orderline | 67862 | 67862 | PASS |
| fact_invoice | c_invoice | 1182 | 1182 | PASS |
| fact_invoice_line | c_invoiceline | 62146 | 62146 | PASS |
| fact_delivery | m_inout | 4667 | 4667 | PASS |
| fact_delivery_line | m_inoutline | 181631 | 181631 | PASS |
| fact_payment_allocation | c_allocationline | 245 | 245 | PASS |
| fact_stock_snapshot | rv_storage | 58826 | 58826 | PASS |

## Financial Total Checks

| Check | Source value | Output value | Difference | Status |
|---|---:|---:|---:|---|
| sales_order_grand_total | 16709787.1106 | 16709787.1106 | 0.0 | PASS |
| sales_order_line_net_amount | 16709710.3906 | 16709710.3906 | 0.0 | PASS |
| invoice_grand_total | 16757554.02 | 16757554.02 | 0.0 | PASS |
| invoice_line_net_amount | 16774348.62 | 16774348.62 | 0.0 | PASS |
| payment_allocation_amount | 1706047.19 | 1706047.19 | 0.0 | PASS |

## Zero-Key Profile

Rows with key `0` use the unknown member. They are expected when the ERP export is missing an optional reference.

| Fact | Key | Zero rows | Row count | Rate |
|---|---|---:|---:|---:|
| fact_sales_order | sales_order_key | 0 | 3350 | 0.0 |
| fact_sales_order | order_date_key | 0 | 3350 | 0.0 |
| fact_sales_order | customer_key | 0 | 3350 | 0.0 |
| fact_sales_order | commercial_key | 0 | 3350 | 0.0 |
| fact_sales_order | geography_key | 4 | 3350 | 0.0012 |
| fact_sales_order | payment_term_key | 0 | 3350 | 0.0 |
| fact_sales_order | price_list_key | 0 | 3350 | 0.0 |
| fact_sales_order | document_type_key | 0 | 3350 | 0.0 |
| fact_sales_order | warehouse_key | 0 | 3350 | 0.0 |
| fact_sales_order_line | sales_order_line_key | 0 | 67862 | 0.0 |
| fact_sales_order_line | order_date_key | 0 | 67862 | 0.0 |
| fact_sales_order_line | customer_key | 0 | 67862 | 0.0 |
| fact_sales_order_line | commercial_key | 0 | 67862 | 0.0 |
| fact_sales_order_line | product_key | 0 | 67862 | 0.0 |
| fact_sales_order_line | product_category_key | 0 | 67862 | 0.0 |
| fact_sales_order_line | supplier_key | 331 | 67862 | 0.0049 |
| fact_sales_order_line | warehouse_key | 0 | 67862 | 0.0 |
| fact_sales_order_line | document_type_key | 0 | 67862 | 0.0 |
| fact_invoice | invoice_key | 0 | 1182 | 0.0 |
| fact_invoice | invoice_date_key | 0 | 1182 | 0.0 |
| fact_invoice | customer_key | 0 | 1182 | 0.0 |
| fact_invoice | commercial_key | 0 | 1182 | 0.0 |
| fact_invoice | geography_key | 13 | 1182 | 0.011 |
| fact_invoice | payment_term_key | 0 | 1182 | 0.0 |
| fact_invoice | price_list_key | 0 | 1182 | 0.0 |
| fact_invoice | document_type_key | 0 | 1182 | 0.0 |
| fact_invoice_line | invoice_line_key | 0 | 62146 | 0.0 |
| fact_invoice_line | invoice_date_key | 0 | 62146 | 0.0 |
| fact_invoice_line | customer_key | 0 | 62146 | 0.0 |
| fact_invoice_line | commercial_key | 0 | 62146 | 0.0 |
| fact_invoice_line | product_key | 0 | 62146 | 0.0 |
| fact_invoice_line | product_category_key | 0 | 62146 | 0.0 |
| fact_invoice_line | supplier_key | 2064 | 62146 | 0.0332 |
| fact_invoice_line | document_type_key | 0 | 62146 | 0.0 |
| fact_delivery | delivery_key | 0 | 4667 | 0.0 |
| fact_delivery | movement_date_key | 0 | 4667 | 0.0 |
| fact_delivery | customer_key | 0 | 4667 | 0.0 |
| fact_delivery | geography_key | 4 | 4667 | 0.0009 |
| fact_delivery | warehouse_key | 0 | 4667 | 0.0 |
| fact_delivery | document_type_key | 4667 | 4667 | 1.0 |
| fact_delivery_line | delivery_line_key | 0 | 181631 | 0.0 |
| fact_delivery_line | movement_date_key | 0 | 181631 | 0.0 |
| fact_delivery_line | customer_key | 0 | 181631 | 0.0 |
| fact_delivery_line | product_key | 10 | 181631 | 0.0001 |
| fact_delivery_line | product_category_key | 10 | 181631 | 0.0001 |
| fact_delivery_line | supplier_key | 144 | 181631 | 0.0008 |
| fact_delivery_line | warehouse_key | 0 | 181631 | 0.0 |
| fact_payment_allocation | payment_allocation_key | 0 | 245 | 0.0 |
| fact_payment_allocation | allocation_date_key | 0 | 245 | 0.0 |
| fact_payment_allocation | customer_key | 0 | 245 | 0.0 |
| fact_payment_allocation | commercial_key | 245 | 245 | 1.0 |
| fact_payment_allocation | payment_document_type_key | 245 | 245 | 1.0 |
| fact_stock_snapshot | stock_snapshot_key | 0 | 58826 | 0.0 |
| fact_stock_snapshot | snapshot_date_key | 0 | 58826 | 0.0 |
| fact_stock_snapshot | product_key | 0 | 58826 | 0.0 |
| fact_stock_snapshot | product_category_key | 0 | 58826 | 0.0 |
| fact_stock_snapshot | supplier_key | 3334 | 58826 | 0.0567 |
| fact_stock_snapshot | warehouse_key | 42250 | 58826 | 0.7182 |
