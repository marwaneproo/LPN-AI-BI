-- =============================================================================
-- File     : docs/bi-service-roadmap/implementation/sql/marts_business/03_materialize.sql
-- Task     : IMPL-04 - Materialize heavy BI marts over business.*
-- Status   : REAL DB CHANGE - mart schema only, idempotent
--
-- Purpose  : Promote measured heavy marts to materialized views without changing
--            the BI-08 column contract. Light marts remain plain views.
--
-- Decision : IMPL-DECISION-02 - heavy marts are materialized views refreshed
--            after ETL. Current measured offenders: mart_sales_by_product and
--            mart_order_to_invoice_flow.
--
-- Safety   : No business/app objects are modified. This script only replaces
--            mart.mart_sales_by_product with a materialized view of the same
--            name and same columns.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS mart;

GRANT USAGE ON SCHEMA mart TO lpn_ai_readonly;

DO $$
DECLARE
    current_relkind "char";
BEGIN
    SELECT c.relkind
    INTO current_relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'mart'
      AND c.relname = 'mart_sales_by_product';

    IF current_relkind = 'm' THEN
        EXECUTE 'DROP MATERIALIZED VIEW mart.mart_sales_by_product';
    ELSIF current_relkind = 'v' THEN
        EXECUTE 'DROP VIEW mart.mart_sales_by_product';
    END IF;
END $$;

CREATE MATERIALIZED VIEW mart.mart_sales_by_product AS
WITH invoice_line_sales AS (
    SELECT
        i.dateinvoiced::date AS metric_date,
        to_char(i.dateinvoiced::date, 'YYYYMMDD')::integer AS date_key,
        COALESCE(il.m_product_id, 0) AS product_key,
        COALESCE(p.m_product_category_id, il.m_product_id, 0) AS product_category_key,
        COALESCE(vps.supplier_id, 0) AS supplier_key,
        COALESCE(i.c_bpartner_id, 0) AS customer_key,
        COUNT(DISTINCT i.c_invoice_id) AS invoice_count,
        COUNT(DISTINCT il.c_invoiceline_id) AS invoice_line_count,
        SUM(il.qtyinvoiced) AS quantity_invoiced,
        SUM(il.linenetamt) AS ca_facture
    FROM business.c_invoiceline il
    JOIN business.c_invoice i ON i.c_invoice_id = il.c_invoice_id
    LEFT JOIN business.m_product p ON p.m_product_id = il.m_product_id
    LEFT JOIN business.v_product_primary_supplier vps ON vps.m_product_id = il.m_product_id
    WHERE i.dateinvoiced IS NOT NULL
      AND COALESCE(i.issotrx, 'Y') = 'Y'
      AND i.docstatus = 'CO'
    GROUP BY
        i.dateinvoiced::date,
        COALESCE(il.m_product_id, 0),
        COALESCE(p.m_product_category_id, il.m_product_id, 0),
        COALESCE(vps.supplier_id, 0),
        COALESCE(i.c_bpartner_id, 0)
),
stock_by_product AS (
    SELECT
        COALESCE(rs.m_product_id, 0) AS product_key,
        SUM(rs.qtyonhand) AS qty_on_hand,
        SUM(rs.qtyreserved) AS qty_reserved,
        SUM(rs.qtyavailable) AS qty_available,
        SUM(rs.qtyordered) AS qty_ordered
    FROM business.rv_storage rs
    GROUP BY COALESCE(rs.m_product_id, 0)
)
SELECT
    ils.date_key,
    ils.metric_date,
    date_trunc('month', ils.metric_date)::date AS period_month,
    COALESCE(ils.product_key, 0) AS product_key,
    COALESCE(p.m_product_id, 0) AS m_product_id,
    COALESCE(NULLIF(p.name, ''), 'Produit non renseigne') AS product_name,
    COALESCE(ils.product_category_key, 0) AS product_category_key,
    COALESCE(pc.m_product_category_id, 0) AS m_product_category_id,
    COALESCE(NULLIF(pc.name, ''), 'Categorie non renseignee') AS category_name,
    COALESCE(NULLIF(pt.name, ''), 'Type non renseigne') AS product_type_name,
    COALESCE(NULLIF(th.name, ''), 'Thematique non renseignee') AS theme_name,
    COALESCE(NULLIF(col.name, ''), 'Collection non renseignee') AS collection_name,
    COALESCE(ils.supplier_key, 0) AS supplier_key,
    COALESCE(vps.supplier_id, 0) AS supplier_id,
    COALESCE(NULLIF(vps.supplier_name, ''), 'Fournisseur non renseigne') AS supplier_name,
    COALESCE(ils.customer_key, 0) AS customer_key,
    COALESCE(bp.c_bpartner_id, 0) AS c_bpartner_id,
    COALESCE(NULLIF(bp.name, ''), 'Client non renseigne') AS customer_name,
    COALESCE(ils.invoice_count, 0) AS invoice_count,
    COALESCE(ils.invoice_line_count, 0) AS invoice_line_count,
    COALESCE(ils.quantity_invoiced, 0) AS quantity_invoiced,
    COALESCE(ils.ca_facture, 0) AS ca_facture,
    CASE WHEN COALESCE(ils.product_key, 0) <> 0 THEN 1 ELSE 0 END AS nombre_produits_vendus,
    COALESCE(st.qty_on_hand, 0) AS qty_on_hand,
    COALESCE(st.qty_reserved, 0) AS qty_reserved,
    COALESCE(st.qty_available, 0) AS qty_available,
    COALESCE(st.qty_ordered, 0) AS qty_ordered
FROM invoice_line_sales ils
LEFT JOIN business.m_product p ON p.m_product_id = ils.product_key
LEFT JOIN business.m_product_category pc ON pc.m_product_category_id = p.m_product_category_id
LEFT JOIN business.m_product_type pt ON pt.m_product_type_id = p.m_product_type_id
LEFT JOIN business.m_product_theme th ON th.m_product_theme_id = p.m_product_theme_id
LEFT JOIN business.m_product_collection col ON col.m_product_collection_id = p.m_product_collection_id
LEFT JOIN business.v_product_primary_supplier vps ON vps.m_product_id = ils.product_key
LEFT JOIN business.c_bpartner bp ON bp.c_bpartner_id = ils.customer_key
LEFT JOIN stock_by_product st ON st.product_key = ils.product_key;

CREATE INDEX IF NOT EXISTS ix_mart_sales_by_product_metric_date
    ON mart.mart_sales_by_product (metric_date);

CREATE INDEX IF NOT EXISTS ix_mart_sales_by_product_date_key
    ON mart.mart_sales_by_product (date_key);

CREATE INDEX IF NOT EXISTS ix_mart_sales_by_product_period_month
    ON mart.mart_sales_by_product (period_month);

CREATE INDEX IF NOT EXISTS ix_mart_sales_by_product_product_key
    ON mart.mart_sales_by_product (product_key, m_product_id);

CREATE INDEX IF NOT EXISTS ix_mart_sales_by_product_category_key
    ON mart.mart_sales_by_product (product_category_key, m_product_category_id);

CREATE INDEX IF NOT EXISTS ix_mart_sales_by_product_supplier_key
    ON mart.mart_sales_by_product (supplier_key, supplier_id);

CREATE INDEX IF NOT EXISTS ix_mart_sales_by_product_customer_key
    ON mart.mart_sales_by_product (customer_key, c_bpartner_id);

CREATE INDEX IF NOT EXISTS ix_mart_sales_by_product_ca_facture
    ON mart.mart_sales_by_product (ca_facture DESC);

CREATE INDEX IF NOT EXISTS ix_mart_sales_by_product_labels
    ON mart.mart_sales_by_product (category_name, theme_name, collection_name);

GRANT SELECT ON mart.mart_sales_by_product TO lpn_ai_readonly;

ANALYZE mart.mart_sales_by_product;

DO $$
DECLARE
    current_relkind "char";
BEGIN
    SELECT c.relkind
    INTO current_relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'mart'
      AND c.relname = 'mart_order_to_invoice_flow';

    IF current_relkind = 'm' THEN
        EXECUTE 'DROP MATERIALIZED VIEW mart.mart_order_to_invoice_flow';
    ELSIF current_relkind = 'v' THEN
        EXECUTE 'DROP VIEW mart.mart_order_to_invoice_flow';
    END IF;
END $$;

CREATE MATERIALIZED VIEW mart.mart_order_to_invoice_flow AS
WITH invoice_line_by_orderline AS (
    SELECT
        il.c_orderline_id,
        SUM(il.linenetamt) AS invoiced_line_amount
    FROM business.c_invoiceline il
    JOIN business.c_invoice i ON i.c_invoice_id = il.c_invoice_id
    WHERE il.c_orderline_id IS NOT NULL
      AND COALESCE(i.issotrx, 'Y') = 'Y'
      AND i.docstatus = 'CO'
    GROUP BY il.c_orderline_id
)
SELECT
    o.dateordered::date AS metric_date,
    to_char(o.dateordered::date, 'YYYYMMDD')::integer AS date_key,
    date_trunc('month', o.dateordered::date)::date AS period_month,
    COALESCE(o.c_doctypetarget_id, o.c_doctype_id, 0) AS document_type_key,
    COALESCE(dt.c_doctype_id, 0) AS c_doctype_id,
    COALESCE(NULLIF(dt.name, ''), 'Type non renseigne') AS document_type_name,
    COALESCE(o.docstatus, 'UNKNOWN') AS doc_status,
    COALESCE(o.salesrep_id, o.ad_user_id, 0) AS commercial_key,
    COALESCE(NULLIF(sr.salesrep_name, ''), NULLIF(u.name, ''), 'Commercial non renseigne') AS commercial_name,
    COUNT(DISTINCT o.c_order_id) AS nombre_commandes,
    COUNT(DISTINCT ol.c_orderline_id) AS order_line_count,
    SUM(ol.linenetamt) AS ca_commande,
    SUM(ol.qtyordered) AS quantity_ordered,
    SUM(ol.qtydelivered) AS quantity_delivered,
    SUM(ol.qtyinvoiced) AS quantity_invoiced,
    SUM(COALESCE(il.invoiced_line_amount, 0)) AS ca_facture_ligne,
    SUM(ol.qtyordered) - SUM(ol.qtydelivered) AS quantity_delivery_gap,
    SUM(ol.qtyordered) - SUM(ol.qtyinvoiced) AS quantity_invoice_gap,
    CASE
        WHEN SUM(ol.qtyordered) = 0 THEN 0
        ELSE ROUND(SUM(ol.qtydelivered) * 100 / NULLIF(SUM(ol.qtyordered), 0), 2)
    END AS quantity_delivery_coverage_percent,
    CASE
        WHEN SUM(ol.qtyordered) = 0 THEN 0
        ELSE ROUND(SUM(ol.qtyinvoiced) * 100 / NULLIF(SUM(ol.qtyordered), 0), 2)
    END AS quantity_invoice_coverage_percent,
    CASE
        WHEN SUM(ol.linenetamt) = 0 THEN 0
        ELSE ROUND(SUM(COALESCE(il.invoiced_line_amount, 0)) * 100 / NULLIF(SUM(ol.linenetamt), 0), 2)
    END AS amount_invoice_coverage_percent
FROM business.c_orderline ol
JOIN business.c_order o ON o.c_order_id = ol.c_order_id
LEFT JOIN invoice_line_by_orderline il ON il.c_orderline_id = ol.c_orderline_id
LEFT JOIN business.c_doctype dt ON dt.c_doctype_id = COALESCE(o.c_doctypetarget_id, o.c_doctype_id)
LEFT JOIN business.v_salesrep_user sr ON sr.salesrep_id = COALESCE(o.salesrep_id, o.ad_user_id, 0)
LEFT JOIN business.ad_user u ON u.ad_user_id = COALESCE(o.salesrep_id, o.ad_user_id, 0)
WHERE o.dateordered IS NOT NULL
  AND COALESCE(o.issotrx, 'Y') = 'Y'
  AND o.docstatus IN ('CO', 'CL')
GROUP BY
    o.dateordered::date,
    COALESCE(o.c_doctypetarget_id, o.c_doctype_id, 0),
    dt.c_doctype_id,
    dt.name,
    o.docstatus,
    COALESCE(o.salesrep_id, o.ad_user_id, 0),
    COALESCE(NULLIF(sr.salesrep_name, ''), NULLIF(u.name, ''), 'Commercial non renseigne');

CREATE INDEX IF NOT EXISTS ix_mart_order_to_invoice_flow_metric_date
    ON mart.mart_order_to_invoice_flow (metric_date);

CREATE INDEX IF NOT EXISTS ix_mart_order_to_invoice_flow_date_key
    ON mart.mart_order_to_invoice_flow (date_key);

CREATE INDEX IF NOT EXISTS ix_mart_order_to_invoice_flow_period_month
    ON mart.mart_order_to_invoice_flow (period_month);

CREATE INDEX IF NOT EXISTS ix_mart_order_to_invoice_flow_document_type
    ON mart.mart_order_to_invoice_flow (document_type_key, c_doctype_id);

CREATE INDEX IF NOT EXISTS ix_mart_order_to_invoice_flow_commercial
    ON mart.mart_order_to_invoice_flow (commercial_key);

CREATE INDEX IF NOT EXISTS ix_mart_order_to_invoice_flow_status
    ON mart.mart_order_to_invoice_flow (doc_status);

CREATE INDEX IF NOT EXISTS ix_mart_order_to_invoice_flow_ca_commande
    ON mart.mart_order_to_invoice_flow (ca_commande DESC);

GRANT SELECT ON mart.mart_order_to_invoice_flow TO lpn_ai_readonly;

ANALYZE mart.mart_order_to_invoice_flow;
