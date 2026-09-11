-- =============================================================================
-- File     : docs/bi-service-roadmap/implementation/sql/marts_business/01_overview.sql
-- Task     : IMPL-01 - Overview and payment marts over business.*
-- Status   : REAL DB CHANGE - additive, idempotent
--
-- Purpose  : Interim mart views over business.* that preserve the BI-08 column
--            contract until the governed warehouse is materialized.
--
-- Safety   : No ALTER/DROP on business or app. Views only.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS mart;

GRANT USAGE ON SCHEMA mart TO lpn_ai_readonly;

CREATE OR REPLACE VIEW mart.mart_sales_daily AS
WITH order_daily AS (
    SELECT
        o.dateordered::date AS metric_date,
        to_char(o.dateordered::date, 'YYYYMMDD')::integer AS date_key,
        COUNT(DISTINCT o.c_order_id) AS nombre_commandes,
        SUM(o.grandtotal) AS ca_commande,
        COUNT(DISTINCT o.c_bpartner_id) AS active_customers_ordered
    FROM business.c_order o
    WHERE o.dateordered IS NOT NULL
      AND COALESCE(o.issotrx, 'Y') = 'Y'
      AND o.docstatus IN ('CO', 'CL')
    GROUP BY o.dateordered::date
),
invoice_daily AS (
    SELECT
        i.dateinvoiced::date AS metric_date,
        to_char(i.dateinvoiced::date, 'YYYYMMDD')::integer AS date_key,
        COUNT(DISTINCT i.c_invoice_id) AS invoice_count,
        SUM(i.grandtotal) AS ca_facture,
        COUNT(DISTINCT i.c_bpartner_id) AS active_customers_invoiced,
        COUNT(DISTINCT CASE WHEN i.ispaid = 'Y' THEN i.c_invoice_id END) AS paid_invoice_count,
        COUNT(DISTINCT CASE WHEN COALESCE(i.ispaid, 'N') <> 'Y' THEN i.c_invoice_id END) AS unpaid_invoice_count,
        SUM(CASE WHEN COALESCE(i.ispaid, 'N') <> 'Y' THEN i.grandtotal ELSE 0 END) AS unpaid_invoice_amount
    FROM business.c_invoice i
    WHERE i.dateinvoiced IS NOT NULL
      AND COALESCE(i.issotrx, 'Y') = 'Y'
      AND i.docstatus = 'CO'
    GROUP BY i.dateinvoiced::date
),
date_spine AS (
    SELECT metric_date, date_key FROM order_daily
    UNION
    SELECT metric_date, date_key FROM invoice_daily
)
SELECT
    ds.date_key,
    ds.metric_date,
    date_trunc('month', ds.metric_date)::date AS period_month,
    EXTRACT(YEAR FROM ds.metric_date)::integer AS year_number,
    EXTRACT(MONTH FROM ds.metric_date)::integer AS month_number,
    COALESCE(o.nombre_commandes, 0) AS nombre_commandes,
    COALESCE(o.ca_commande, 0) AS ca_commande,
    COALESCE(o.active_customers_ordered, 0) AS active_customers_ordered,
    COALESCE(i.invoice_count, 0) AS invoice_count,
    COALESCE(i.ca_facture, 0) AS ca_facture,
    COALESCE(i.active_customers_invoiced, 0) AS active_customers_invoiced,
    COALESCE(i.paid_invoice_count, 0) AS paid_invoice_count,
    COALESCE(i.unpaid_invoice_count, 0) AS unpaid_invoice_count,
    COALESCE(i.unpaid_invoice_amount, 0) AS unpaid_invoice_amount,
    CASE
        WHEN COALESCE(o.nombre_commandes, 0) = 0 THEN 0
        ELSE ROUND(COALESCE(o.ca_commande, 0) / NULLIF(o.nombre_commandes, 0), 2)
    END AS average_order_value,
    CASE
        WHEN COALESCE(o.ca_commande, 0) = 0 THEN 0
        ELSE ROUND(COALESCE(i.ca_facture, 0) * 100 / NULLIF(o.ca_commande, 0), 2)
    END AS invoice_coverage_percent,
    COALESCE(i.ca_facture, 0) - COALESCE(o.ca_commande, 0) AS invoice_gap_amount
FROM date_spine ds
LEFT JOIN order_daily o ON o.metric_date = ds.metric_date
LEFT JOIN invoice_daily i ON i.metric_date = ds.metric_date;

CREATE OR REPLACE VIEW mart.mart_sales_monthly AS
SELECT
    period_month,
    EXTRACT(YEAR FROM period_month)::integer AS year_number,
    EXTRACT(MONTH FROM period_month)::integer AS month_number,
    SUM(nombre_commandes) AS nombre_commandes,
    SUM(ca_commande) AS ca_commande,
    SUM(active_customers_ordered) AS active_customers_ordered_sum,
    SUM(invoice_count) AS invoice_count,
    SUM(ca_facture) AS ca_facture,
    SUM(active_customers_invoiced) AS active_customers_invoiced_sum,
    SUM(paid_invoice_count) AS paid_invoice_count,
    SUM(unpaid_invoice_count) AS unpaid_invoice_count,
    SUM(unpaid_invoice_amount) AS unpaid_invoice_amount,
    CASE
        WHEN SUM(nombre_commandes) = 0 THEN 0
        ELSE ROUND(SUM(ca_commande) / NULLIF(SUM(nombre_commandes), 0), 2)
    END AS average_order_value,
    CASE
        WHEN SUM(ca_commande) = 0 THEN 0
        ELSE ROUND(SUM(ca_facture) * 100 / NULLIF(SUM(ca_commande), 0), 2)
    END AS invoice_coverage_percent,
    SUM(ca_facture) - SUM(ca_commande) AS invoice_gap_amount
FROM mart.mart_sales_daily
GROUP BY period_month;

CREATE OR REPLACE VIEW mart.mart_payment_status AS
WITH invoice_status AS (
    SELECT
        i.dateinvoiced::date AS metric_date,
        to_char(i.dateinvoiced::date, 'YYYYMMDD')::integer AS date_key,
        COALESCE(i.c_bpartner_id, 0) AS customer_key,
        COALESCE(i.salesrep_id, i.commercial_id, i.ad_user_id, 0) AS commercial_key,
        i.ispaid = 'Y' AS is_paid,
        COUNT(DISTINCT i.c_invoice_id) AS invoice_count,
        SUM(i.grandtotal) AS ca_facture,
        COUNT(DISTINCT CASE WHEN i.ispaid = 'Y' THEN i.c_invoice_id END) AS paid_invoice_count,
        COUNT(DISTINCT CASE WHEN COALESCE(i.ispaid, 'N') <> 'Y' THEN i.c_invoice_id END) AS unpaid_invoice_count,
        SUM(CASE WHEN COALESCE(i.ispaid, 'N') <> 'Y' THEN i.grandtotal ELSE 0 END) AS unpaid_invoice_amount
    FROM business.c_invoice i
    WHERE i.dateinvoiced IS NOT NULL
      AND COALESCE(i.issotrx, 'Y') = 'Y'
      AND i.docstatus = 'CO'
    GROUP BY
        i.dateinvoiced::date,
        COALESCE(i.c_bpartner_id, 0),
        COALESCE(i.salesrep_id, i.commercial_id, i.ad_user_id, 0),
        i.ispaid = 'Y'
),
allocation_status AS (
    SELECT
        COALESCE(al.datetrx, ah.datetrx, p.datetrx)::date AS metric_date,
        to_char(COALESCE(al.datetrx, ah.datetrx, p.datetrx)::date, 'YYYYMMDD')::integer AS date_key,
        COALESCE(al.c_bpartner_id, i.c_bpartner_id, p.c_bpartner_id, 0) AS customer_key,
        COALESCE(i.salesrep_id, i.commercial_id, i.ad_user_id, p.ad_user_id, 0) AS commercial_key,
        SUM(al.amount) AS allocated_amount,
        SUM(al.discountamt) AS discount_amount,
        SUM(al.writeoffamt) AS writeoff_amount,
        COUNT(DISTINCT al.c_allocationline_id) AS allocation_count
    FROM business.c_allocationline al
    LEFT JOIN business.c_allocationhdr ah ON ah.c_allocationhdr_id = al.c_allocationhdr_id
    LEFT JOIN business.c_payment p ON p.c_payment_id = al.c_payment_id
    LEFT JOIN business.c_invoice i ON i.c_invoice_id = al.c_invoice_id
    WHERE COALESCE(al.datetrx, ah.datetrx, p.datetrx) IS NOT NULL
      AND COALESCE(ah.docstatus, 'CO') = 'CO'
    GROUP BY
        COALESCE(al.datetrx, ah.datetrx, p.datetrx)::date,
        COALESCE(al.c_bpartner_id, i.c_bpartner_id, p.c_bpartner_id, 0),
        COALESCE(i.salesrep_id, i.commercial_id, i.ad_user_id, p.ad_user_id, 0)
)
SELECT
    COALESCE(i.date_key, a.date_key) AS date_key,
    COALESCE(i.metric_date, a.metric_date) AS metric_date,
    date_trunc('month', COALESCE(i.metric_date, a.metric_date))::date AS period_month,
    COALESCE(i.customer_key, a.customer_key, 0) AS customer_key,
    COALESCE(bp.c_bpartner_id, 0) AS c_bpartner_id,
    COALESCE(NULLIF(bp.name, ''), 'Client non renseigne') AS customer_name,
    COALESCE(i.commercial_key, a.commercial_key, 0) AS commercial_key,
    COALESCE(NULLIF(u.name, ''), 'Commercial non renseigne') AS commercial_name,
    COALESCE(i.is_paid, false) AS is_paid,
    CASE WHEN COALESCE(i.is_paid, false) THEN 'PAID' ELSE 'UNPAID' END AS payment_status,
    COALESCE(i.invoice_count, 0) AS invoice_count,
    COALESCE(i.ca_facture, 0) AS ca_facture,
    COALESCE(i.paid_invoice_count, 0) AS paid_invoice_count,
    COALESCE(i.unpaid_invoice_count, 0) AS unpaid_invoice_count,
    COALESCE(i.unpaid_invoice_amount, 0) AS unpaid_invoice_amount,
    COALESCE(a.allocated_amount, 0) AS allocated_amount,
    COALESCE(a.discount_amount, 0) AS discount_amount,
    COALESCE(a.writeoff_amount, 0) AS writeoff_amount,
    COALESCE(a.allocation_count, 0) AS allocation_count
FROM invoice_status i
FULL OUTER JOIN allocation_status a
    ON a.metric_date = i.metric_date
   AND a.customer_key = i.customer_key
   AND a.commercial_key = i.commercial_key
LEFT JOIN business.c_bpartner bp ON bp.c_bpartner_id = COALESCE(i.customer_key, a.customer_key, 0)
LEFT JOIN business.ad_user u ON u.ad_user_id = COALESCE(i.commercial_key, a.commercial_key, 0);

GRANT SELECT ON
    mart.mart_sales_daily,
    mart.mart_sales_monthly,
    mart.mart_payment_status
TO lpn_ai_readonly;
