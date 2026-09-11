-- =============================================================================
-- LPN AI-BI  ·  Mart serving views
-- Task     : DW-08 - Mart serving views for 5 BI pages + stock
-- Date     : 2026-06-29
-- Target   : PostgreSQL / schema mart
--
-- Contract:
--   * Reads warehouse.dim_* and warehouse.fact_* only.
--   * Does not read or modify business.*, staging.*, source files, or raw extracts.
--   * Reuses docs/semantic_layer/metrics.yml formulas:
--       ca_commande              = SUM(C_ORDER.GRANDTOTAL)
--       ca_facture               = SUM(C_INVOICE.GRANDTOTAL)
--       product/article CA       = SUM(C_INVOICELINE.LINENETAMT)
--       nombre_commandes         = COUNT(DISTINCT C_ORDER.C_ORDER_ID)
--       nombre_clients_actifs    = COUNT(DISTINCT C_ORDER.C_BPARTNER_ID)
--       nombre_produits_vendus   = COUNT(DISTINCT C_INVOICELINE.M_PRODUCT_ID)
--       paid/unpaid invoices     = C_INVOICE.ISPAID
--       stock_disponible         = SUM(RV_STORAGE.QTYAVAILABLE) at one snapshot
--   * Idempotent: DROP+CREATE is used because older mart views were business-backed.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS mart;

COMMENT ON SCHEMA mart IS
  'BI serving mart views over warehouse.dim_* and warehouse.fact_* for LPN AI-BI.';

DO $$
DECLARE
    mart_rel RECORD;
BEGIN
    FOR mart_rel IN
        SELECT c.relkind, n.nspname, c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'mart'
          AND c.relname IN (
              'mart_stock_risk',
              'mart_payment_status',
              'mart_order_to_invoice_flow',
              'mart_sales_by_region',
              'mart_sales_by_product',
              'mart_sales_by_customer',
              'mart_sales_by_commercial',
              'mart_sales_monthly',
              'mart_sales_daily',
              'mart_stock',
              'mart_commercial',
              'mart_clients',
              'mart_articles',
              'mart_revenue',
              'mart_commandes',
              'mart_overview'
          )
        ORDER BY
          CASE c.relkind
            WHEN 'v' THEN 1
            WHEN 'm' THEN 2
            ELSE 3
          END
    LOOP
        IF mart_rel.relkind = 'm' THEN
            EXECUTE format('DROP MATERIALIZED VIEW IF EXISTS %I.%I CASCADE', mart_rel.nspname, mart_rel.relname);
        ELSIF mart_rel.relkind = 'v' THEN
            EXECUTE format('DROP VIEW IF EXISTS %I.%I CASCADE', mart_rel.nspname, mart_rel.relname);
        ELSE
            EXECUTE format('DROP TABLE IF EXISTS %I.%I CASCADE', mart_rel.nspname, mart_rel.relname);
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- mart_overview
-- Grain: date_grain + period_start. Carries executive KPI totals.
-- ---------------------------------------------------------------------------
CREATE VIEW mart.mart_overview AS
WITH order_period AS (
    SELECT
        g.date_grain,
        g.period_start,
        g.period_end,
        COUNT(DISTINCT f.c_order_id) AS nombre_commandes,
        SUM(f.grand_total_amount) AS ca_commande,
        COUNT(DISTINCT NULLIF(f.customer_key, 0)) AS active_customers_ordered
    FROM warehouse.fact_sales_order f
    JOIN warehouse.dim_date d ON d.date_key = f.order_date_key
    CROSS JOIN LATERAL (
        VALUES
            ('day'::text, d.full_date, d.full_date + 1),
            ('week'::text, date_trunc('week', d.full_date)::date, date_trunc('week', d.full_date)::date + 7),
            ('month'::text, date_trunc('month', d.full_date)::date, (date_trunc('month', d.full_date)::date + INTERVAL '1 month')::date)
    ) AS g(date_grain, period_start, period_end)
    WHERE f.order_date_key <> 0
    GROUP BY g.date_grain, g.period_start, g.period_end
),
invoice_period AS (
    SELECT
        g.date_grain,
        g.period_start,
        g.period_end,
        COUNT(DISTINCT f.c_invoice_id) AS invoice_count,
        SUM(f.grand_total_amount) AS ca_facture,
        COUNT(DISTINCT NULLIF(f.customer_key, 0)) AS active_customers_invoiced,
        SUM(f.paid_invoice_count) AS factures_payees,
        SUM(f.unpaid_invoice_count) AS factures_impayees,
        SUM(CASE WHEN f.is_paid IS NOT TRUE THEN f.grand_total_amount ELSE 0 END) AS unpaid_invoice_amount
    FROM warehouse.fact_invoice f
    JOIN warehouse.dim_date d ON d.date_key = f.invoice_date_key
    CROSS JOIN LATERAL (
        VALUES
            ('day'::text, d.full_date, d.full_date + 1),
            ('week'::text, date_trunc('week', d.full_date)::date, date_trunc('week', d.full_date)::date + 7),
            ('month'::text, date_trunc('month', d.full_date)::date, (date_trunc('month', d.full_date)::date + INTERVAL '1 month')::date)
    ) AS g(date_grain, period_start, period_end)
    WHERE f.invoice_date_key <> 0
    GROUP BY g.date_grain, g.period_start, g.period_end
),
product_period AS (
    SELECT
        g.date_grain,
        g.period_start,
        g.period_end,
        COUNT(DISTINCT NULLIF(f.product_key, 0)) AS nombre_produits_vendus
    FROM warehouse.fact_invoice_line f
    JOIN warehouse.dim_date d ON d.date_key = f.invoice_date_key
    CROSS JOIN LATERAL (
        VALUES
            ('day'::text, d.full_date, d.full_date + 1),
            ('week'::text, date_trunc('week', d.full_date)::date, date_trunc('week', d.full_date)::date + 7),
            ('month'::text, date_trunc('month', d.full_date)::date, (date_trunc('month', d.full_date)::date + INTERVAL '1 month')::date)
    ) AS g(date_grain, period_start, period_end)
    WHERE f.invoice_date_key <> 0
    GROUP BY g.date_grain, g.period_start, g.period_end
),
spine AS (
    SELECT date_grain, period_start, period_end FROM order_period
    UNION
    SELECT date_grain, period_start, period_end FROM invoice_period
    UNION
    SELECT date_grain, period_start, period_end FROM product_period
)
SELECT
    s.date_grain,
    to_char(s.period_start, 'YYYYMMDD')::integer AS period_key,
    s.period_start,
    s.period_end,
    date_trunc('week', s.period_start)::date AS period_week,
    date_trunc('month', s.period_start)::date AS period_month,
    EXTRACT(YEAR FROM s.period_start)::integer AS year_number,
    EXTRACT(QUARTER FROM s.period_start)::integer AS quarter_number,
    EXTRACT(MONTH FROM s.period_start)::integer AS month_number,
    EXTRACT(WEEK FROM s.period_start)::integer AS week_number,
    COALESCE(o.nombre_commandes, 0) AS nombre_commandes,
    COALESCE(o.ca_commande, 0) AS ca_commande,
    COALESCE(o.active_customers_ordered, 0) AS active_customers_ordered,
    COALESCE(i.invoice_count, 0) AS invoice_count,
    COALESCE(i.ca_facture, 0) AS ca_facture,
    COALESCE(i.active_customers_invoiced, 0) AS active_customers_invoiced,
    COALESCE(o.active_customers_ordered, 0) AS nombre_clients_actifs,
    COALESCE(p.nombre_produits_vendus, 0) AS nombre_produits_vendus,
    COALESCE(i.factures_payees, 0) AS factures_payees,
    COALESCE(i.factures_impayees, 0) AS factures_impayees,
    COALESCE(i.unpaid_invoice_amount, 0) AS unpaid_invoice_amount,
    CASE
        WHEN COALESCE(o.nombre_commandes, 0) = 0 THEN 0
        ELSE ROUND(COALESCE(o.ca_commande, 0) / NULLIF(o.nombre_commandes, 0), 2)
    END AS average_order_value,
    CASE
        WHEN COALESCE(o.ca_commande, 0) = 0 THEN 0
        ELSE ROUND(COALESCE(i.ca_facture, 0) * 100 / NULLIF(o.ca_commande, 0), 2)
    END AS couverture_facturation_pct,
    CASE
        WHEN COALESCE(o.nombre_commandes, 0) = 0 THEN 0
        ELSE ROUND(COALESCE(i.invoice_count, 0)::numeric * 100 / NULLIF(o.nombre_commandes, 0), 2)
    END AS couverture_factures_count_pct,
    COALESCE(i.ca_facture, 0) - COALESCE(o.ca_commande, 0) AS invoice_gap_amount
FROM spine s
LEFT JOIN order_period o
    ON o.date_grain = s.date_grain
   AND o.period_start = s.period_start
   AND o.period_end = s.period_end
LEFT JOIN invoice_period i
    ON i.date_grain = s.date_grain
   AND i.period_start = s.period_start
   AND i.period_end = s.period_end
LEFT JOIN product_period p
    ON p.date_grain = s.date_grain
   AND p.period_start = s.period_start
   AND p.period_end = s.period_end;

COMMENT ON VIEW mart.mart_overview IS
  'Overview KPIs by day/week/month. Warehouse-backed; CA formulas come from metrics.yml.';

-- ---------------------------------------------------------------------------
-- mart_commandes
-- Grain: date_grain + period_start + document type + doc status + commercial.
-- ---------------------------------------------------------------------------
CREATE VIEW mart.mart_commandes AS
WITH order_period AS (
    SELECT
        g.date_grain,
        g.period_start,
        g.period_end,
        COALESCE(f.document_type_key, 0) AS document_type_key,
        COALESCE(f.doc_status, 'UNKNOWN') AS doc_status,
        COALESCE(f.commercial_key, 0) AS commercial_key,
        COUNT(DISTINCT f.c_order_id) AS nombre_commandes,
        SUM(f.grand_total_amount) AS ca_commande
    FROM warehouse.fact_sales_order f
    JOIN warehouse.dim_date d ON d.date_key = f.order_date_key
    CROSS JOIN LATERAL (
        VALUES
            ('day'::text, d.full_date, d.full_date + 1),
            ('week'::text, date_trunc('week', d.full_date)::date, date_trunc('week', d.full_date)::date + 7),
            ('month'::text, date_trunc('month', d.full_date)::date, (date_trunc('month', d.full_date)::date + INTERVAL '1 month')::date)
    ) AS g(date_grain, period_start, period_end)
    WHERE f.order_date_key <> 0
    GROUP BY g.date_grain, g.period_start, g.period_end, f.document_type_key, f.doc_status, f.commercial_key
),
invoice_line_by_orderline AS (
    SELECT
        f.c_orderline_id,
        SUM(f.line_net_amount) AS invoiced_line_amount
    FROM warehouse.fact_invoice_line f
    WHERE f.c_orderline_id IS NOT NULL
    GROUP BY f.c_orderline_id
),
line_period AS (
    SELECT
        g.date_grain,
        g.period_start,
        g.period_end,
        COALESCE(f.document_type_key, 0) AS document_type_key,
        COALESCE(f.doc_status, 'UNKNOWN') AS doc_status,
        COALESCE(f.commercial_key, 0) AS commercial_key,
        COUNT(DISTINCT f.c_order_id) AS order_count_from_lines,
        COUNT(DISTINCT f.c_orderline_id) AS order_line_count,
        SUM(f.line_net_amount) AS ca_commande_ligne,
        SUM(COALESCE(il.invoiced_line_amount, 0)) AS ca_facture_ligne,
        SUM(f.quantity_ordered) AS quantity_ordered,
        SUM(f.quantity_delivered) AS quantity_delivered,
        SUM(f.quantity_invoiced) AS quantity_invoiced
    FROM warehouse.fact_sales_order_line f
    JOIN warehouse.dim_date d ON d.date_key = f.order_date_key
    CROSS JOIN LATERAL (
        VALUES
            ('day'::text, d.full_date, d.full_date + 1),
            ('week'::text, date_trunc('week', d.full_date)::date, date_trunc('week', d.full_date)::date + 7),
            ('month'::text, date_trunc('month', d.full_date)::date, (date_trunc('month', d.full_date)::date + INTERVAL '1 month')::date)
    ) AS g(date_grain, period_start, period_end)
    LEFT JOIN invoice_line_by_orderline il ON il.c_orderline_id = f.c_orderline_id
    WHERE f.order_date_key <> 0
    GROUP BY g.date_grain, g.period_start, g.period_end, f.document_type_key, f.doc_status, f.commercial_key
),
spine AS (
    SELECT date_grain, period_start, period_end, document_type_key, doc_status, commercial_key FROM order_period
    UNION
    SELECT date_grain, period_start, period_end, document_type_key, doc_status, commercial_key FROM line_period
)
SELECT
    s.date_grain,
    to_char(s.period_start, 'YYYYMMDD')::integer AS period_key,
    s.period_start,
    s.period_end,
    date_trunc('month', s.period_start)::date AS period_month,
    EXTRACT(YEAR FROM s.period_start)::integer AS year_number,
    COALESCE(s.document_type_key, 0) AS document_type_key,
    COALESCE(dt.c_doctype_id, 0) AS c_doctype_id,
    COALESCE(dt.document_type_name, 'Type non renseigne') AS document_type_name,
    COALESCE(s.doc_status, 'UNKNOWN') AS doc_status,
    COALESCE(s.commercial_key, 0) AS commercial_key,
    COALESCE(c.ad_user_id, 0) AS ad_user_id,
    COALESCE(c.commercial_name, 'Commercial non renseigne') AS commercial_name,
    COALESCE(o.nombre_commandes, l.order_count_from_lines, 0) AS nombre_commandes,
    COALESCE(l.order_line_count, 0) AS order_line_count,
    COALESCE(o.ca_commande, 0) AS ca_commande,
    COALESCE(l.ca_commande_ligne, 0) AS ca_commande_ligne,
    COALESCE(l.ca_facture_ligne, 0) AS ca_facture_ligne,
    COALESCE(l.quantity_ordered, 0) AS quantity_ordered,
    COALESCE(l.quantity_delivered, 0) AS quantity_delivered,
    COALESCE(l.quantity_invoiced, 0) AS quantity_invoiced,
    COALESCE(l.quantity_ordered, 0) - COALESCE(l.quantity_delivered, 0) AS quantity_delivery_gap,
    COALESCE(l.quantity_ordered, 0) - COALESCE(l.quantity_invoiced, 0) AS quantity_invoice_gap,
    CASE
        WHEN COALESCE(l.quantity_ordered, 0) = 0 THEN 0
        ELSE ROUND(COALESCE(l.quantity_delivered, 0) * 100 / NULLIF(l.quantity_ordered, 0), 2)
    END AS couverture_livraison_pct,
    CASE
        WHEN COALESCE(l.quantity_ordered, 0) = 0 THEN 0
        ELSE ROUND(COALESCE(l.quantity_invoiced, 0) * 100 / NULLIF(l.quantity_ordered, 0), 2)
    END AS couverture_facturation_pct,
    CASE
        WHEN COALESCE(l.ca_commande_ligne, 0) = 0 THEN 0
        ELSE ROUND(COALESCE(l.ca_facture_ligne, 0) * 100 / NULLIF(l.ca_commande_ligne, 0), 2)
    END AS amount_invoice_coverage_percent
FROM spine s
LEFT JOIN order_period o
    ON o.date_grain = s.date_grain
   AND o.period_start = s.period_start
   AND o.period_end = s.period_end
   AND o.document_type_key = s.document_type_key
   AND o.doc_status = s.doc_status
   AND o.commercial_key = s.commercial_key
LEFT JOIN line_period l
    ON l.date_grain = s.date_grain
   AND l.period_start = s.period_start
   AND l.period_end = s.period_end
   AND l.document_type_key = s.document_type_key
   AND l.doc_status = s.doc_status
   AND l.commercial_key = s.commercial_key
LEFT JOIN warehouse.dim_document_type dt ON dt.document_type_key = COALESCE(s.document_type_key, 0)
LEFT JOIN warehouse.dim_commercial c ON c.commercial_key = COALESCE(s.commercial_key, 0);

COMMENT ON VIEW mart.mart_commandes IS
  'Order volume, ordered CA, order type/status trend, and delivery/invoice coverage.';

-- ---------------------------------------------------------------------------
-- mart_revenue
-- Grain: date_grain + period_start. Carries CA facture vs commande and payment exposure.
-- ---------------------------------------------------------------------------
CREATE VIEW mart.mart_revenue AS
WITH delivery_flow AS (
    SELECT
        date_grain,
        period_start,
        SUM(quantity_delivery_gap) AS quantity_delivery_gap,
        SUM(quantity_invoice_gap) AS quantity_invoice_gap
    FROM mart.mart_commandes
    GROUP BY date_grain, period_start
)
SELECT
    o.date_grain,
    o.period_key,
    o.period_start,
    o.period_end,
    o.period_month,
    o.year_number,
    'ALL'::text AS payment_status,
    NULL::boolean AS is_paid,
    o.ca_facture,
    o.ca_commande,
    o.ca_facture AS invoiced_sales,
    o.ca_commande AS ordered_sales,
    o.invoice_gap_amount,
    o.average_order_value,
    o.couverture_facturation_pct,
    o.couverture_factures_count_pct,
    o.nombre_commandes,
    o.invoice_count,
    o.factures_payees,
    o.factures_impayees,
    o.unpaid_invoice_amount,
    COALESCE(f.quantity_delivery_gap, 0) AS quantity_delivery_gap,
    COALESCE(f.quantity_invoice_gap, 0) AS quantity_invoice_gap
FROM mart.mart_overview o
LEFT JOIN delivery_flow f
    ON f.date_grain = o.date_grain
   AND f.period_start = o.period_start;

COMMENT ON VIEW mart.mart_revenue IS
  'Revenue page mart: CA facture vs CA commande, coverage, paid/unpaid exposure, trend.';

-- ---------------------------------------------------------------------------
-- mart_articles
-- Grain: date_grain + period_start + product + category/type/theme/collection
--        + supplier + customer.
-- ---------------------------------------------------------------------------
CREATE VIEW mart.mart_articles AS
SELECT
    g.date_grain,
    to_char(g.period_start, 'YYYYMMDD')::integer AS period_key,
    g.period_start,
    g.period_end,
    date_trunc('month', g.period_start)::date AS period_month,
    EXTRACT(YEAR FROM g.period_start)::integer AS year_number,
    COALESCE(f.product_key, 0) AS product_key,
    COALESCE(p.m_product_id, 0) AS m_product_id,
    COALESCE(p.product_code, 'UNKNOWN') AS product_code,
    COALESCE(p.product_name, 'Produit non renseigne') AS product_name,
    COALESCE(f.product_category_key, 0) AS product_category_key,
    COALESCE(pc.m_product_category_id, 0) AS m_product_category_id,
    COALESCE(pc.category_name, 'Categorie non renseignee') AS category_name,
    COALESCE(p.product_type_name, 'Type non renseigne') AS product_type_name,
    COALESCE(p.theme_name, 'Thematique non renseignee') AS theme_name,
    COALESCE(p.collection_name, 'Collection non renseignee') AS collection_name,
    COALESCE(f.supplier_key, 0) AS supplier_key,
    COALESCE(su.c_bpartner_id, 0) AS supplier_id,
    COALESCE(su.supplier_name, 'Fournisseur non renseigne') AS supplier_name,
    COALESCE(f.customer_key, 0) AS customer_key,
    COALESCE(cu.c_bpartner_id, 0) AS c_bpartner_id,
    COALESCE(cu.customer_name, 'Client non renseigne') AS customer_name,
    COUNT(DISTINCT f.c_invoice_id) AS invoice_count,
    COUNT(DISTINCT f.c_invoiceline_id) AS invoice_line_count,
    SUM(f.quantity_invoiced) AS quantity_invoiced,
    SUM(f.line_net_amount) AS ca_facture,
    CASE WHEN COALESCE(f.product_key, 0) <> 0 THEN 1 ELSE 0 END AS nombre_produits_vendus
FROM warehouse.fact_invoice_line f
JOIN warehouse.dim_date d ON d.date_key = f.invoice_date_key
CROSS JOIN LATERAL (
    VALUES
        ('day'::text, d.full_date, d.full_date + 1),
        ('week'::text, date_trunc('week', d.full_date)::date, date_trunc('week', d.full_date)::date + 7),
        ('month'::text, date_trunc('month', d.full_date)::date, (date_trunc('month', d.full_date)::date + INTERVAL '1 month')::date)
) AS g(date_grain, period_start, period_end)
LEFT JOIN warehouse.dim_product p ON p.product_key = COALESCE(f.product_key, 0)
LEFT JOIN warehouse.dim_product_category pc ON pc.product_category_key = COALESCE(f.product_category_key, 0)
LEFT JOIN warehouse.dim_supplier su ON su.supplier_key = COALESCE(f.supplier_key, 0)
LEFT JOIN warehouse.dim_customer cu ON cu.customer_key = COALESCE(f.customer_key, 0)
WHERE f.invoice_date_key <> 0
GROUP BY
    g.date_grain,
    g.period_start,
    g.period_end,
    f.product_key,
    p.m_product_id,
    p.product_code,
    p.product_name,
    f.product_category_key,
    pc.m_product_category_id,
    pc.category_name,
    p.product_type_name,
    p.theme_name,
    p.collection_name,
    f.supplier_key,
    su.c_bpartner_id,
    su.supplier_name,
    f.customer_key,
    cu.c_bpartner_id,
    cu.customer_name;

COMMENT ON VIEW mart.mart_articles IS
  'Article/product sales mart. Product CA uses invoice line net amount per metrics.yml.';

-- ---------------------------------------------------------------------------
-- mart_clients
-- Grain: date_grain + period_start + customer + commercial + geography.
-- ---------------------------------------------------------------------------
CREATE VIEW mart.mart_clients AS
WITH order_period AS (
    SELECT
        g.date_grain,
        g.period_start,
        g.period_end,
        COALESCE(f.customer_key, 0) AS customer_key,
        COALESCE(f.commercial_key, 0) AS commercial_key,
        COALESCE(f.geography_key, 0) AS geography_key,
        COUNT(DISTINCT f.c_order_id) AS nombre_commandes,
        SUM(f.grand_total_amount) AS ca_commande
    FROM warehouse.fact_sales_order f
    JOIN warehouse.dim_date d ON d.date_key = f.order_date_key
    CROSS JOIN LATERAL (
        VALUES
            ('day'::text, d.full_date, d.full_date + 1),
            ('week'::text, date_trunc('week', d.full_date)::date, date_trunc('week', d.full_date)::date + 7),
            ('month'::text, date_trunc('month', d.full_date)::date, (date_trunc('month', d.full_date)::date + INTERVAL '1 month')::date)
    ) AS g(date_grain, period_start, period_end)
    WHERE f.order_date_key <> 0
    GROUP BY g.date_grain, g.period_start, g.period_end, f.customer_key, f.commercial_key, f.geography_key
),
invoice_period AS (
    SELECT
        g.date_grain,
        g.period_start,
        g.period_end,
        COALESCE(f.customer_key, 0) AS customer_key,
        COALESCE(f.commercial_key, 0) AS commercial_key,
        COALESCE(f.geography_key, 0) AS geography_key,
        COUNT(DISTINCT f.c_invoice_id) AS invoice_count,
        SUM(f.grand_total_amount) AS ca_facture,
        SUM(f.paid_invoice_count) AS factures_payees,
        SUM(f.unpaid_invoice_count) AS factures_impayees,
        SUM(CASE WHEN f.is_paid IS NOT TRUE THEN f.grand_total_amount ELSE 0 END) AS unpaid_invoice_amount
    FROM warehouse.fact_invoice f
    JOIN warehouse.dim_date d ON d.date_key = f.invoice_date_key
    CROSS JOIN LATERAL (
        VALUES
            ('day'::text, d.full_date, d.full_date + 1),
            ('week'::text, date_trunc('week', d.full_date)::date, date_trunc('week', d.full_date)::date + 7),
            ('month'::text, date_trunc('month', d.full_date)::date, (date_trunc('month', d.full_date)::date + INTERVAL '1 month')::date)
    ) AS g(date_grain, period_start, period_end)
    WHERE f.invoice_date_key <> 0
    GROUP BY g.date_grain, g.period_start, g.period_end, f.customer_key, f.commercial_key, f.geography_key
),
spine AS (
    SELECT date_grain, period_start, period_end, customer_key, commercial_key, geography_key FROM order_period
    UNION
    SELECT date_grain, period_start, period_end, customer_key, commercial_key, geography_key FROM invoice_period
)
SELECT
    s.date_grain,
    to_char(s.period_start, 'YYYYMMDD')::integer AS period_key,
    s.period_start,
    s.period_end,
    date_trunc('month', s.period_start)::date AS period_month,
    EXTRACT(YEAR FROM s.period_start)::integer AS year_number,
    COALESCE(s.customer_key, 0) AS customer_key,
    COALESCE(cu.c_bpartner_id, 0) AS c_bpartner_id,
    COALESCE(cu.customer_name, 'Client non renseigne') AS customer_name,
    COALESCE(s.commercial_key, 0) AS commercial_key,
    COALESCE(co.ad_user_id, 0) AS ad_user_id,
    COALESCE(co.commercial_name, 'Commercial non renseigne') AS commercial_name,
    COALESCE(s.geography_key, 0) AS geography_key,
    COALESCE(g.city_name, 'Ville non renseignee') AS city_name,
    COALESCE(g.region_name, 'Region non renseignee') AS region_name,
    COALESCE(sr.sales_region_name, 'Region commerciale non renseignee') AS sales_region_name,
    COALESCE(o.nombre_commandes, 0) AS nombre_commandes,
    COALESCE(o.ca_commande, 0) AS ca_commande,
    COALESCE(i.invoice_count, 0) AS invoice_count,
    COALESCE(i.ca_facture, 0) AS ca_facture,
    COALESCE(i.factures_payees, 0) AS factures_payees,
    COALESCE(i.factures_impayees, 0) AS factures_impayees,
    COALESCE(i.unpaid_invoice_amount, 0) AS unpaid_invoice_amount,
    CASE WHEN COALESCE(s.customer_key, 0) <> 0 THEN 1 ELSE 0 END AS active_customer_flag,
    CASE
        WHEN COALESCE(i.invoice_count, 0) = 0 THEN 0
        ELSE ROUND(COALESCE(i.ca_facture, 0) / NULLIF(i.invoice_count, 0), 2)
    END AS average_invoice_value
FROM spine s
LEFT JOIN order_period o
    ON o.date_grain = s.date_grain
   AND o.period_start = s.period_start
   AND o.period_end = s.period_end
   AND o.customer_key = s.customer_key
   AND o.commercial_key = s.commercial_key
   AND o.geography_key = s.geography_key
LEFT JOIN invoice_period i
    ON i.date_grain = s.date_grain
   AND i.period_start = s.period_start
   AND i.period_end = s.period_end
   AND i.customer_key = s.customer_key
   AND i.commercial_key = s.commercial_key
   AND i.geography_key = s.geography_key
LEFT JOIN warehouse.dim_customer cu ON cu.customer_key = COALESCE(s.customer_key, 0)
LEFT JOIN warehouse.dim_commercial co ON co.commercial_key = COALESCE(s.commercial_key, 0)
LEFT JOIN warehouse.dim_geography g ON g.geography_key = COALESCE(s.geography_key, 0)
LEFT JOIN warehouse.dim_sales_region sr ON sr.sales_region_key = COALESCE(g.sales_region_key, 0);

COMMENT ON VIEW mart.mart_clients IS
  'Customer portfolio mart: ordered/invoiced CA, active customers, geography, finance exposure.';

-- ---------------------------------------------------------------------------
-- mart_commercial
-- Grain: month + commercial.
-- ---------------------------------------------------------------------------
CREATE VIEW mart.mart_commercial AS
WITH order_month AS (
    SELECT
        date_trunc('month', d.full_date)::date AS period_month,
        COALESCE(f.commercial_key, 0) AS commercial_key,
        COUNT(DISTINCT f.c_order_id) AS nombre_commandes,
        SUM(f.grand_total_amount) AS ca_commande
    FROM warehouse.fact_sales_order f
    JOIN warehouse.dim_date d ON d.date_key = f.order_date_key
    WHERE f.order_date_key <> 0
    GROUP BY date_trunc('month', d.full_date)::date, f.commercial_key
),
invoice_month AS (
    SELECT
        date_trunc('month', d.full_date)::date AS period_month,
        COALESCE(f.commercial_key, 0) AS commercial_key,
        COUNT(DISTINCT f.c_invoice_id) AS invoice_count,
        SUM(f.grand_total_amount) AS ca_facture
    FROM warehouse.fact_invoice f
    JOIN warehouse.dim_date d ON d.date_key = f.invoice_date_key
    WHERE f.invoice_date_key <> 0
    GROUP BY date_trunc('month', d.full_date)::date, f.commercial_key
),
spine AS (
    SELECT period_month, commercial_key FROM order_month
    UNION
    SELECT period_month, commercial_key FROM invoice_month
)
SELECT
    'month'::text AS date_grain,
    to_char(s.period_month, 'YYYYMMDD')::integer AS period_key,
    s.period_month,
    (s.period_month + INTERVAL '1 month')::date AS period_end,
    EXTRACT(YEAR FROM s.period_month)::integer AS year_number,
    EXTRACT(MONTH FROM s.period_month)::integer AS month_number,
    COALESCE(s.commercial_key, 0) AS commercial_key,
    COALESCE(c.ad_user_id, 0) AS ad_user_id,
    COALESCE(c.commercial_name, 'Commercial non renseigne') AS commercial_name,
    c.email AS salesrep_email,
    CASE WHEN COALESCE(s.commercial_key, 0) <> 0 THEN 1 ELSE 0 END AS commercial_count,
    COALESCE(o.nombre_commandes, 0) AS nombre_commandes,
    COALESCE(o.ca_commande, 0) AS ca_commande,
    COALESCE(i.invoice_count, 0) AS invoice_count,
    COALESCE(i.ca_facture, 0) AS ca_facture,
    COALESCE(i.ca_facture, 0) - COALESCE(o.ca_commande, 0) AS invoice_gap_amount,
    CASE
        WHEN COALESCE(o.ca_commande, 0) = 0 THEN 0
        ELSE ROUND(COALESCE(i.ca_facture, 0) * 100 / NULLIF(o.ca_commande, 0), 2)
    END AS couverture_facturation_pct
FROM spine s
LEFT JOIN order_month o ON o.period_month = s.period_month AND o.commercial_key = s.commercial_key
LEFT JOIN invoice_month i ON i.period_month = s.period_month AND i.commercial_key = s.commercial_key
LEFT JOIN warehouse.dim_commercial c ON c.commercial_key = COALESCE(s.commercial_key, 0);

COMMENT ON VIEW mart.mart_commercial IS
  'Commercial monthly mart: ordered and invoiced CA by sales representative.';

-- ---------------------------------------------------------------------------
-- mart_stock
-- Grain: latest/current stock snapshot + product + warehouse + locator + attrset.
-- Note: DW-07 found snapshot_date_key=0 for all rows because DATELASTINVENTORY is NULL.
-- ---------------------------------------------------------------------------
CREATE VIEW mart.mart_stock AS
WITH latest_snapshot AS (
    SELECT COALESCE(MAX(NULLIF(snapshot_date_key, 0)), 0) AS snapshot_date_key
    FROM warehouse.fact_stock_snapshot
)
SELECT
    'snapshot'::text AS date_grain,
    f.snapshot_date_key,
    d.full_date AS snapshot_date,
    date_trunc('month', d.full_date)::date AS period_month,
    CASE WHEN f.snapshot_date_key = 0 THEN true ELSE false END AS unknown_snapshot_date,
    COALESCE(f.product_key, 0) AS product_key,
    COALESCE(p.m_product_id, 0) AS m_product_id,
    COALESCE(p.product_name, 'Produit non renseigne') AS product_name,
    COALESCE(f.product_category_key, 0) AS product_category_key,
    COALESCE(pc.m_product_category_id, 0) AS m_product_category_id,
    COALESCE(pc.category_name, 'Categorie non renseignee') AS category_name,
    COALESCE(p.product_type_name, 'Type non renseigne') AS product_type_name,
    COALESCE(p.theme_name, 'Thematique non renseignee') AS theme_name,
    COALESCE(f.supplier_key, 0) AS supplier_key,
    COALESCE(su.c_bpartner_id, 0) AS supplier_id,
    COALESCE(su.supplier_name, 'Fournisseur non renseigne') AS supplier_name,
    COALESCE(f.warehouse_key, 0) AS warehouse_key,
    COALESCE(w.warehouse_name, 'Depot non renseigne') AS warehouse_name,
    COALESCE(f.m_locator_id, 0) AS m_locator_id,
    COALESCE(f.m_attribute_set_instance_id, 0) AS m_attribute_set_instance_id,
    SUM(f.quantity_on_hand) AS qty_on_hand,
    SUM(f.quantity_reserved) AS qty_reserved,
    SUM(f.quantity_available) AS qty_available,
    SUM(f.quantity_available) AS stock_disponible,
    SUM(f.quantity_ordered) AS qty_ordered,
    COUNT(*) AS stock_row_count,
    CASE WHEN SUM(f.quantity_available) <= 0 THEN 1 ELSE 0 END AS produits_stock_risque,
    CASE WHEN SUM(f.quantity_available) <= 0 THEN true ELSE false END AS is_stockout_risk,
    CASE
        WHEN SUM(f.quantity_available) <= 0 THEN 'critical'
        WHEN SUM(f.quantity_available) <= SUM(f.quantity_reserved) THEN 'low'
        ELSE 'ok'
    END AS stock_risk_level
FROM warehouse.fact_stock_snapshot f
JOIN latest_snapshot ls ON f.snapshot_date_key = ls.snapshot_date_key
LEFT JOIN warehouse.dim_date d ON d.date_key = f.snapshot_date_key
LEFT JOIN warehouse.dim_product p ON p.product_key = COALESCE(f.product_key, 0)
LEFT JOIN warehouse.dim_product_category pc ON pc.product_category_key = COALESCE(f.product_category_key, 0)
LEFT JOIN warehouse.dim_supplier su ON su.supplier_key = COALESCE(f.supplier_key, 0)
LEFT JOIN warehouse.dim_warehouse w ON w.warehouse_key = COALESCE(f.warehouse_key, 0)
GROUP BY
    f.snapshot_date_key,
    d.full_date,
    f.product_key,
    p.m_product_id,
    p.product_name,
    f.product_category_key,
    pc.m_product_category_id,
    pc.category_name,
    p.product_type_name,
    p.theme_name,
    f.supplier_key,
    su.c_bpartner_id,
    su.supplier_name,
    f.warehouse_key,
    w.warehouse_name,
    f.m_locator_id,
    f.m_attribute_set_instance_id;

COMMENT ON VIEW mart.mart_stock IS
  'Current stock snapshot. Stock is semi-additive; do not sum across multiple snapshots.';

-- =============================================================================
-- Compatibility / API helper views currently read by the Spring BI repositories.
-- These are also warehouse-backed and keep the existing column names.
-- =============================================================================

CREATE MATERIALIZED VIEW mart.mart_sales_daily AS
SELECT
    period_key AS date_key,
    period_start AS metric_date,
    period_month,
    year_number,
    month_number,
    nombre_commandes,
    ca_commande,
    active_customers_ordered,
    invoice_count,
    ca_facture,
    active_customers_invoiced,
    factures_payees AS paid_invoice_count,
    factures_impayees AS unpaid_invoice_count,
    unpaid_invoice_amount,
    average_order_value,
    couverture_facturation_pct AS invoice_coverage_percent,
    invoice_gap_amount
FROM mart.mart_overview
WHERE date_grain = 'day';

CREATE MATERIALIZED VIEW mart.mart_sales_monthly AS
SELECT
    period_start AS period_month,
    year_number,
    month_number,
    nombre_commandes,
    ca_commande,
    active_customers_ordered AS active_customers_ordered_sum,
    invoice_count,
    ca_facture,
    active_customers_invoiced AS active_customers_invoiced_sum,
    factures_payees AS paid_invoice_count,
    factures_impayees AS unpaid_invoice_count,
    unpaid_invoice_amount,
    average_order_value,
    couverture_facturation_pct AS invoice_coverage_percent,
    invoice_gap_amount
FROM mart.mart_overview
WHERE date_grain = 'month';

CREATE MATERIALIZED VIEW mart.mart_sales_by_commercial AS
WITH order_sales AS (
    SELECT
        f.order_date_key AS date_key,
        COALESCE(f.commercial_key, 0) AS commercial_dim_key,
        COUNT(DISTINCT f.c_order_id) AS nombre_commandes,
        SUM(f.grand_total_amount) AS ca_commande
    FROM warehouse.fact_sales_order f
    WHERE f.order_date_key <> 0
    GROUP BY f.order_date_key, f.commercial_key
),
invoice_sales AS (
    SELECT
        f.invoice_date_key AS date_key,
        COALESCE(f.commercial_key, 0) AS commercial_dim_key,
        COUNT(DISTINCT f.c_invoice_id) AS invoice_count,
        SUM(f.grand_total_amount) AS ca_facture
    FROM warehouse.fact_invoice f
    WHERE f.invoice_date_key <> 0
    GROUP BY f.invoice_date_key, f.commercial_key
),
spine AS (
    SELECT date_key, commercial_dim_key FROM order_sales
    UNION
    SELECT date_key, commercial_dim_key FROM invoice_sales
)
SELECT
    s.date_key,
    d.full_date AS metric_date,
    date_trunc('month', d.full_date)::date AS period_month,
    COALESCE(c.ad_user_id, 0) AS commercial_key,
    COALESCE(c.ad_user_id, 0) AS ad_user_id,
    COALESCE(s.commercial_dim_key, 0) AS commercial_dim_key,
    COALESCE(c.commercial_name, 'Commercial non renseigne') AS commercial_name,
    c.email AS salesrep_email,
    COALESCE(o.nombre_commandes, 0) AS nombre_commandes,
    COALESCE(o.ca_commande, 0) AS ca_commande,
    COALESCE(i.invoice_count, 0) AS invoice_count,
    COALESCE(i.ca_facture, 0) AS ca_facture,
    CASE
        WHEN COALESCE(o.ca_commande, 0) = 0 THEN 0
        ELSE ROUND(COALESCE(i.ca_facture, 0) * 100 / NULLIF(o.ca_commande, 0), 2)
    END AS invoice_coverage_percent,
    COALESCE(i.ca_facture, 0) - COALESCE(o.ca_commande, 0) AS invoice_gap_amount
FROM spine s
JOIN warehouse.dim_date d ON d.date_key = s.date_key
LEFT JOIN warehouse.dim_commercial c ON c.commercial_key = COALESCE(s.commercial_dim_key, 0)
LEFT JOIN order_sales o ON o.date_key = s.date_key AND o.commercial_dim_key = s.commercial_dim_key
LEFT JOIN invoice_sales i ON i.date_key = s.date_key AND i.commercial_dim_key = s.commercial_dim_key;

CREATE MATERIALIZED VIEW mart.mart_sales_by_customer AS
SELECT
    period_key AS date_key,
    period_start AS metric_date,
    period_month,
    customer_key,
    c_bpartner_id,
    customer_name,
    ad_user_id AS commercial_key,
    commercial_key AS commercial_dim_key,
    commercial_name,
    geography_key,
    city_name,
    region_name,
    nombre_commandes,
    ca_commande,
    invoice_count,
    ca_facture,
    factures_payees AS paid_invoice_count,
    factures_impayees AS unpaid_invoice_count,
    unpaid_invoice_amount,
    active_customer_flag,
    average_invoice_value
FROM mart.mart_clients
WHERE date_grain = 'day';

CREATE MATERIALIZED VIEW mart.mart_sales_by_product AS
SELECT
    period_key AS date_key,
    period_start AS metric_date,
    period_month,
    product_key,
    m_product_id,
    product_name,
    product_category_key,
    m_product_category_id,
    category_name,
    product_type_name,
    theme_name,
    collection_name,
    supplier_key,
    supplier_id,
    supplier_name,
    customer_key,
    c_bpartner_id,
    customer_name,
    invoice_count,
    invoice_line_count,
    quantity_invoiced,
    ca_facture,
    nombre_produits_vendus
FROM mart.mart_articles
WHERE date_grain = 'day';

CREATE MATERIALIZED VIEW mart.mart_sales_by_region AS
SELECT
    period_start AS metric_date,
    period_key AS date_key,
    period_month,
    geography_key,
    city_name,
    region_name,
    MIN(COALESCE(NULLIF(sales_region_name, 'Region commerciale non renseignee'), 'Region commerciale non renseignee')) AS sales_region_name,
    COUNT(DISTINCT NULLIF(customer_key, 0)) AS customer_count,
    SUM(invoice_count) AS invoice_count,
    SUM(ca_facture) AS ca_facture
FROM mart.mart_clients
WHERE date_grain = 'day'
GROUP BY period_start, period_key, period_month, geography_key, city_name, region_name;

CREATE MATERIALIZED VIEW mart.mart_order_to_invoice_flow AS
SELECT
    period_key AS date_key,
    period_start AS metric_date,
    period_month,
    document_type_key,
    c_doctype_id,
    document_type_name,
    doc_status,
    ad_user_id AS commercial_key,
    commercial_key AS commercial_dim_key,
    commercial_name,
    nombre_commandes,
    order_line_count,
    ca_commande,
    quantity_ordered,
    quantity_delivered,
    quantity_invoiced,
    ca_facture_ligne,
    quantity_delivery_gap,
    quantity_invoice_gap,
    couverture_livraison_pct AS quantity_delivery_coverage_percent,
    couverture_facturation_pct AS quantity_invoice_coverage_percent,
    amount_invoice_coverage_percent
FROM mart.mart_commandes
WHERE date_grain = 'day';

CREATE MATERIALIZED VIEW mart.mart_payment_status AS
WITH invoice_status AS (
    SELECT
        f.invoice_date_key AS date_key,
        COALESCE(f.customer_key, 0) AS customer_dim_key,
        COALESCE(f.commercial_key, 0) AS commercial_dim_key,
        COALESCE(f.is_paid, false) AS is_paid,
        COUNT(DISTINCT f.c_invoice_id) AS invoice_count,
        SUM(f.grand_total_amount) AS ca_facture,
        SUM(f.paid_invoice_count) AS paid_invoice_count,
        SUM(f.unpaid_invoice_count) AS unpaid_invoice_count,
        SUM(CASE WHEN f.is_paid IS NOT TRUE THEN f.grand_total_amount ELSE 0 END) AS unpaid_invoice_amount
    FROM warehouse.fact_invoice f
    WHERE f.invoice_date_key <> 0
    GROUP BY f.invoice_date_key, f.customer_key, f.commercial_key, f.is_paid
),
allocation_status AS (
    SELECT
        f.allocation_date_key AS date_key,
        COALESCE(f.customer_key, 0) AS customer_dim_key,
        COALESCE(f.commercial_key, 0) AS commercial_dim_key,
        SUM(f.allocated_amount) AS allocated_amount,
        SUM(f.discount_amount) AS discount_amount,
        SUM(f.writeoff_amount) AS writeoff_amount,
        COUNT(DISTINCT f.c_allocationline_id) AS allocation_count
    FROM warehouse.fact_payment_allocation f
    WHERE f.allocation_date_key <> 0
    GROUP BY f.allocation_date_key, f.customer_key, f.commercial_key
),
spine AS (
    SELECT date_key, customer_dim_key, commercial_dim_key, is_paid FROM invoice_status
    UNION
    SELECT date_key, customer_dim_key, commercial_dim_key, false AS is_paid FROM allocation_status
)
SELECT
    s.date_key,
    d.full_date AS metric_date,
    date_trunc('month', d.full_date)::date AS period_month,
    COALESCE(cu.customer_key, 0) AS customer_key,
    COALESCE(cu.c_bpartner_id, 0) AS c_bpartner_id,
    COALESCE(cu.customer_name, 'Client non renseigne') AS customer_name,
    COALESCE(co.ad_user_id, 0) AS commercial_key,
    COALESCE(co.commercial_key, 0) AS commercial_dim_key,
    COALESCE(co.commercial_name, 'Commercial non renseigne') AS commercial_name,
    s.is_paid,
    CASE WHEN s.is_paid THEN 'PAID' ELSE 'UNPAID' END AS payment_status,
    COALESCE(i.invoice_count, 0) AS invoice_count,
    COALESCE(i.ca_facture, 0) AS ca_facture,
    COALESCE(i.paid_invoice_count, 0) AS paid_invoice_count,
    COALESCE(i.unpaid_invoice_count, 0) AS unpaid_invoice_count,
    COALESCE(i.unpaid_invoice_amount, 0) AS unpaid_invoice_amount,
    COALESCE(a.allocated_amount, 0) AS allocated_amount,
    COALESCE(a.discount_amount, 0) AS discount_amount,
    COALESCE(a.writeoff_amount, 0) AS writeoff_amount,
    COALESCE(a.allocation_count, 0) AS allocation_count
FROM spine s
JOIN warehouse.dim_date d ON d.date_key = s.date_key
LEFT JOIN invoice_status i
    ON i.date_key = s.date_key
   AND i.customer_dim_key = s.customer_dim_key
   AND i.commercial_dim_key = s.commercial_dim_key
   AND i.is_paid = s.is_paid
LEFT JOIN allocation_status a
    ON a.date_key = s.date_key
   AND a.customer_dim_key = s.customer_dim_key
   AND a.commercial_dim_key = s.commercial_dim_key
LEFT JOIN warehouse.dim_customer cu ON cu.customer_key = COALESCE(s.customer_dim_key, 0)
LEFT JOIN warehouse.dim_commercial co ON co.commercial_key = COALESCE(s.commercial_dim_key, 0);

CREATE MATERIALIZED VIEW mart.mart_stock_risk AS
SELECT
    snapshot_date_key AS date_key,
    snapshot_date,
    period_month,
    product_key,
    m_product_id,
    product_name,
    product_category_key,
    category_name,
    theme_name,
    supplier_key,
    supplier_name,
    warehouse_key,
    warehouse_name,
    SUM(qty_on_hand) AS qty_on_hand,
    SUM(qty_reserved) AS qty_reserved,
    SUM(qty_available) AS qty_available,
    SUM(qty_ordered) AS qty_ordered,
    COUNT(DISTINCT NULLIF(product_key, 0)) AS product_count,
    SUM(produits_stock_risque) AS produits_stock_risque,
    CASE WHEN SUM(qty_available) <= 0 THEN true ELSE false END AS is_stockout_risk,
    CASE
        WHEN SUM(qty_available) <= 0 THEN 'critical'
        WHEN SUM(qty_available) <= SUM(qty_reserved) THEN 'low'
        ELSE 'ok'
    END AS stock_risk_level
FROM mart.mart_stock
GROUP BY
    snapshot_date_key,
    snapshot_date,
    period_month,
    product_key,
    m_product_id,
    product_name,
    product_category_key,
    category_name,
    theme_name,
    supplier_key,
    supplier_name,
    warehouse_key,
    warehouse_name;

-- ---------------------------------------------------------------------------
-- Indexes on the serving materialized views (the BI API filters on metric_date /
-- period_month). Keeps every /v1/bi/* query an index scan over pre-computed data.
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_msd_date    ON mart.mart_sales_daily (metric_date);
CREATE INDEX IF NOT EXISTS idx_msm_month   ON mart.mart_sales_monthly (period_month);
CREATE INDEX IF NOT EXISTS idx_mscom_date  ON mart.mart_sales_by_commercial (metric_date);
CREATE INDEX IF NOT EXISTS idx_mscus_date  ON mart.mart_sales_by_customer (metric_date);
CREATE INDEX IF NOT EXISTS idx_msprod_date ON mart.mart_sales_by_product (metric_date);
CREATE INDEX IF NOT EXISTS idx_msreg_date  ON mart.mart_sales_by_region (metric_date);
CREATE INDEX IF NOT EXISTS idx_mo2i_date   ON mart.mart_order_to_invoice_flow (metric_date);
CREATE INDEX IF NOT EXISTS idx_mps_date    ON mart.mart_payment_status (metric_date);

GRANT USAGE ON SCHEMA mart TO lpn_ai_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA mart TO lpn_ai_readonly;
-- GRANT ON ALL TABLES does NOT cover materialized views in PostgreSQL — grant them explicitly.
GRANT SELECT ON
    mart.mart_sales_daily,
    mart.mart_sales_monthly,
    mart.mart_sales_by_commercial,
    mart.mart_sales_by_customer,
    mart.mart_sales_by_product,
    mart.mart_sales_by_region,
    mart.mart_order_to_invoice_flow,
    mart.mart_payment_status,
    mart.mart_stock_risk
TO lpn_ai_readonly;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA mart
    GRANT SELECT ON TABLES TO lpn_ai_readonly;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lpn_app_admin') THEN
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE lpn_app_admin IN SCHEMA mart GRANT SELECT ON TABLES TO lpn_ai_readonly';
    END IF;
END $$;

-- =============================================================================
-- End of mart DDL
-- =============================================================================
