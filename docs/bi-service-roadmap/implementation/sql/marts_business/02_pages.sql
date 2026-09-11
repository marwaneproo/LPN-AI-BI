-- =============================================================================
-- File     : docs/bi-service-roadmap/implementation/sql/marts_business/02_pages.sql
-- Task     : IMPL-02 - Remaining page marts over business.*
-- Status   : REAL DB CHANGE - additive, idempotent
--
-- Purpose  : Interim mart views over business.* that preserve the BI-08 column
--            contract for the five BI detail pages until the governed warehouse
--            is materialized.
--
-- Safety   : No ALTER/DROP on business or app. Views only.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS mart;

GRANT USAGE ON SCHEMA mart TO lpn_ai_readonly;

CREATE OR REPLACE VIEW mart.mart_sales_by_commercial AS
WITH order_sales AS (
    SELECT
        o.dateordered::date AS metric_date,
        to_char(o.dateordered::date, 'YYYYMMDD')::integer AS date_key,
        COALESCE(o.salesrep_id, o.ad_user_id, 0) AS commercial_key,
        COUNT(DISTINCT o.c_order_id) AS nombre_commandes,
        SUM(o.grandtotal) AS ca_commande
    FROM business.c_order o
    WHERE o.dateordered IS NOT NULL
      AND COALESCE(o.issotrx, 'Y') = 'Y'
      AND o.docstatus IN ('CO', 'CL')
    GROUP BY
        o.dateordered::date,
        COALESCE(o.salesrep_id, o.ad_user_id, 0)
),
invoice_sales AS (
    SELECT
        i.dateinvoiced::date AS metric_date,
        to_char(i.dateinvoiced::date, 'YYYYMMDD')::integer AS date_key,
        COALESCE(i.salesrep_id, i.commercial_id, i.ad_user_id, 0) AS commercial_key,
        COUNT(DISTINCT i.c_invoice_id) AS invoice_count,
        SUM(i.grandtotal) AS ca_facture
    FROM business.c_invoice i
    WHERE i.dateinvoiced IS NOT NULL
      AND COALESCE(i.issotrx, 'Y') = 'Y'
      AND i.docstatus = 'CO'
    GROUP BY
        i.dateinvoiced::date,
        COALESCE(i.salesrep_id, i.commercial_id, i.ad_user_id, 0)
),
spine AS (
    SELECT date_key, metric_date, commercial_key FROM order_sales
    UNION
    SELECT date_key, metric_date, commercial_key FROM invoice_sales
)
SELECT
    s.date_key,
    s.metric_date,
    date_trunc('month', s.metric_date)::date AS period_month,
    COALESCE(s.commercial_key, 0) AS commercial_key,
    COALESCE(s.commercial_key, 0) AS ad_user_id,
    COALESCE(NULLIF(sr.salesrep_name, ''), NULLIF(u.name, ''), 'Commercial non renseigne') AS commercial_name,
    COALESCE(sr.salesrep_email, u.email) AS salesrep_email,
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
LEFT JOIN order_sales o
    ON o.metric_date = s.metric_date
   AND o.commercial_key = s.commercial_key
LEFT JOIN invoice_sales i
    ON i.metric_date = s.metric_date
   AND i.commercial_key = s.commercial_key
LEFT JOIN business.v_salesrep_user sr ON sr.salesrep_id = s.commercial_key
LEFT JOIN business.ad_user u ON u.ad_user_id = s.commercial_key;

CREATE OR REPLACE VIEW mart.mart_sales_by_customer AS
WITH order_sales AS (
    SELECT
        o.dateordered::date AS metric_date,
        to_char(o.dateordered::date, 'YYYYMMDD')::integer AS date_key,
        COALESCE(o.c_bpartner_id, 0) AS customer_key,
        COALESCE(o.salesrep_id, o.ad_user_id, 0) AS commercial_key,
        COALESCE(loc.c_location_id, o.c_bpartner_location_id, 0) AS geography_key,
        COUNT(DISTINCT o.c_order_id) AS nombre_commandes,
        SUM(o.grandtotal) AS ca_commande
    FROM business.c_order o
    LEFT JOIN business.c_bpartner_location bpl ON bpl.c_bpartner_location_id = o.c_bpartner_location_id
    LEFT JOIN business.c_location loc ON loc.c_location_id = bpl.c_location_id
    WHERE o.dateordered IS NOT NULL
      AND COALESCE(o.issotrx, 'Y') = 'Y'
      AND o.docstatus IN ('CO', 'CL')
    GROUP BY
        o.dateordered::date,
        COALESCE(o.c_bpartner_id, 0),
        COALESCE(o.salesrep_id, o.ad_user_id, 0),
        COALESCE(loc.c_location_id, o.c_bpartner_location_id, 0)
),
invoice_sales AS (
    SELECT
        i.dateinvoiced::date AS metric_date,
        to_char(i.dateinvoiced::date, 'YYYYMMDD')::integer AS date_key,
        COALESCE(i.c_bpartner_id, 0) AS customer_key,
        COALESCE(i.salesrep_id, i.commercial_id, i.ad_user_id, 0) AS commercial_key,
        COALESCE(loc.c_location_id, i.c_bpartner_location_id, 0) AS geography_key,
        COUNT(DISTINCT i.c_invoice_id) AS invoice_count,
        SUM(i.grandtotal) AS ca_facture,
        COUNT(DISTINCT CASE WHEN i.ispaid = 'Y' THEN i.c_invoice_id END) AS paid_invoice_count,
        COUNT(DISTINCT CASE WHEN COALESCE(i.ispaid, 'N') <> 'Y' THEN i.c_invoice_id END) AS unpaid_invoice_count,
        SUM(CASE WHEN COALESCE(i.ispaid, 'N') <> 'Y' THEN i.grandtotal ELSE 0 END) AS unpaid_invoice_amount
    FROM business.c_invoice i
    LEFT JOIN business.c_bpartner_location bpl ON bpl.c_bpartner_location_id = i.c_bpartner_location_id
    LEFT JOIN business.c_location loc ON loc.c_location_id = bpl.c_location_id
    WHERE i.dateinvoiced IS NOT NULL
      AND COALESCE(i.issotrx, 'Y') = 'Y'
      AND i.docstatus = 'CO'
    GROUP BY
        i.dateinvoiced::date,
        COALESCE(i.c_bpartner_id, 0),
        COALESCE(i.salesrep_id, i.commercial_id, i.ad_user_id, 0),
        COALESCE(loc.c_location_id, i.c_bpartner_location_id, 0)
),
spine AS (
    SELECT date_key, metric_date, customer_key, commercial_key, geography_key FROM order_sales
    UNION
    SELECT date_key, metric_date, customer_key, commercial_key, geography_key FROM invoice_sales
)
SELECT
    s.date_key,
    s.metric_date,
    date_trunc('month', s.metric_date)::date AS period_month,
    COALESCE(s.customer_key, 0) AS customer_key,
    COALESCE(bp.c_bpartner_id, 0) AS c_bpartner_id,
    COALESCE(NULLIF(bp.name, ''), 'Client non renseigne') AS customer_name,
    COALESCE(s.commercial_key, 0) AS commercial_key,
    COALESCE(NULLIF(sr.salesrep_name, ''), NULLIF(u.name, ''), 'Commercial non renseigne') AS commercial_name,
    COALESCE(s.geography_key, 0) AS geography_key,
    COALESCE(NULLIF(loc.cityname, ''), NULLIF(city.name, ''), NULLIF(loc.city, ''), 'Ville non renseignee') AS city_name,
    COALESCE(NULLIF(region.name, ''), NULLIF(loc.regionname, ''), 'Region non renseignee') AS region_name,
    COALESCE(o.nombre_commandes, 0) AS nombre_commandes,
    COALESCE(o.ca_commande, 0) AS ca_commande,
    COALESCE(i.invoice_count, 0) AS invoice_count,
    COALESCE(i.ca_facture, 0) AS ca_facture,
    COALESCE(i.paid_invoice_count, 0) AS paid_invoice_count,
    COALESCE(i.unpaid_invoice_count, 0) AS unpaid_invoice_count,
    COALESCE(i.unpaid_invoice_amount, 0) AS unpaid_invoice_amount,
    CASE WHEN COALESCE(s.customer_key, 0) <> 0 THEN 1 ELSE 0 END AS active_customer_flag,
    CASE
        WHEN COALESCE(i.invoice_count, 0) = 0 THEN 0
        ELSE ROUND(COALESCE(i.ca_facture, 0) / NULLIF(i.invoice_count, 0), 2)
    END AS average_invoice_value
FROM spine s
LEFT JOIN order_sales o
    ON o.metric_date = s.metric_date
   AND o.customer_key = s.customer_key
   AND o.commercial_key = s.commercial_key
   AND o.geography_key = s.geography_key
LEFT JOIN invoice_sales i
    ON i.metric_date = s.metric_date
   AND i.customer_key = s.customer_key
   AND i.commercial_key = s.commercial_key
   AND i.geography_key = s.geography_key
LEFT JOIN business.c_bpartner bp ON bp.c_bpartner_id = s.customer_key
LEFT JOIN business.v_salesrep_user sr ON sr.salesrep_id = s.commercial_key
LEFT JOIN business.ad_user u ON u.ad_user_id = s.commercial_key
LEFT JOIN business.c_location loc ON loc.c_location_id = s.geography_key
LEFT JOIN business.c_city city ON city.c_city_id = loc.c_city_id
LEFT JOIN business.c_region region ON region.c_region_id = COALESCE(city.c_region_id, loc.c_region_id);

CREATE OR REPLACE VIEW mart.mart_sales_by_product AS
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

CREATE OR REPLACE VIEW mart.mart_sales_by_region AS
SELECT
    i.dateinvoiced::date AS metric_date,
    to_char(i.dateinvoiced::date, 'YYYYMMDD')::integer AS date_key,
    date_trunc('month', i.dateinvoiced::date)::date AS period_month,
    COALESCE(loc.c_location_id, i.c_bpartner_location_id, 0) AS geography_key,
    COALESCE(NULLIF(loc.cityname, ''), NULLIF(city.name, ''), NULLIF(loc.city, ''), 'Ville non renseignee') AS city_name,
    COALESCE(NULLIF(region.name, ''), NULLIF(loc.regionname, ''), 'Region non renseignee') AS region_name,
    COALESCE(bpl.c_salesregion_id, 0) AS sales_region_key,
    COALESCE(NULLIF(sr.name, ''), 'Region commerciale non renseignee') AS sales_region_name,
    COUNT(DISTINCT i.c_bpartner_id) AS customer_count,
    COUNT(DISTINCT i.c_invoice_id) AS invoice_count,
    SUM(i.grandtotal) AS ca_facture
FROM business.c_invoice i
LEFT JOIN business.c_bpartner_location bpl ON bpl.c_bpartner_location_id = i.c_bpartner_location_id
LEFT JOIN business.c_location loc ON loc.c_location_id = bpl.c_location_id
LEFT JOIN business.c_city city ON city.c_city_id = loc.c_city_id
LEFT JOIN business.c_region region ON region.c_region_id = COALESCE(city.c_region_id, loc.c_region_id)
LEFT JOIN business.c_salesregion sr ON sr.c_salesregion_id = bpl.c_salesregion_id
WHERE i.dateinvoiced IS NOT NULL
  AND COALESCE(i.issotrx, 'Y') = 'Y'
  AND i.docstatus = 'CO'
GROUP BY
    i.dateinvoiced::date,
    COALESCE(loc.c_location_id, i.c_bpartner_location_id, 0),
    COALESCE(NULLIF(loc.cityname, ''), NULLIF(city.name, ''), NULLIF(loc.city, ''), 'Ville non renseignee'),
    COALESCE(NULLIF(region.name, ''), NULLIF(loc.regionname, ''), 'Region non renseignee'),
    COALESCE(bpl.c_salesregion_id, 0),
    COALESCE(NULLIF(sr.name, ''), 'Region commerciale non renseignee');

CREATE OR REPLACE VIEW mart.mart_order_to_invoice_flow AS
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

CREATE OR REPLACE VIEW mart.mart_stock_risk AS
SELECT
    to_char(COALESCE(rs.datelastinventory::date, CURRENT_DATE), 'YYYYMMDD')::integer AS date_key,
    COALESCE(rs.datelastinventory::date, CURRENT_DATE) AS snapshot_date,
    date_trunc('month', COALESCE(rs.datelastinventory::date, CURRENT_DATE))::date AS period_month,
    COALESCE(rs.m_product_id, 0) AS product_key,
    COALESCE(p.m_product_id, 0) AS m_product_id,
    COALESCE(NULLIF(p.name, ''), NULLIF(rs.name, ''), 'Produit non renseigne') AS product_name,
    COALESCE(p.m_product_category_id, rs.m_product_category_id, 0) AS product_category_key,
    COALESCE(NULLIF(pc.name, ''), 'Categorie non renseignee') AS category_name,
    COALESCE(NULLIF(th.name, ''), 'Thematique non renseignee') AS theme_name,
    COALESCE(vps.supplier_id, 0) AS supplier_key,
    COALESCE(NULLIF(vps.supplier_name, ''), 'Fournisseur non renseigne') AS supplier_name,
    COALESCE(rs.m_warehouse_id, 0) AS warehouse_key,
    COALESCE(NULLIF(w.name, ''), 'Depot non renseigne') AS warehouse_name,
    SUM(rs.qtyonhand) AS qty_on_hand,
    SUM(rs.qtyreserved) AS qty_reserved,
    SUM(rs.qtyavailable) AS qty_available,
    SUM(rs.qtyordered) AS qty_ordered,
    COUNT(DISTINCT NULLIF(rs.m_product_id, 0)) AS product_count,
    COUNT(DISTINCT CASE WHEN rs.qtyavailable <= 0 THEN NULLIF(rs.m_product_id, 0) END) AS produits_stock_risque,
    CASE WHEN SUM(rs.qtyavailable) <= 0 THEN true ELSE false END AS is_stockout_risk,
    CASE
        WHEN SUM(rs.qtyavailable) <= 0 THEN 'critical'
        WHEN SUM(rs.qtyavailable) <= SUM(rs.qtyreserved) THEN 'low'
        ELSE 'ok'
    END AS stock_risk_level
FROM business.rv_storage rs
LEFT JOIN business.m_product p ON p.m_product_id = rs.m_product_id
LEFT JOIN business.m_product_category pc ON pc.m_product_category_id = COALESCE(p.m_product_category_id, rs.m_product_category_id)
LEFT JOIN business.m_product_theme th ON th.m_product_theme_id = p.m_product_theme_id
LEFT JOIN business.v_product_primary_supplier vps ON vps.m_product_id = rs.m_product_id
LEFT JOIN business.m_warehouse w ON w.m_warehouse_id = rs.m_warehouse_id
GROUP BY
    COALESCE(rs.datelastinventory::date, CURRENT_DATE),
    COALESCE(rs.m_product_id, 0),
    p.m_product_id,
    COALESCE(NULLIF(p.name, ''), NULLIF(rs.name, ''), 'Produit non renseigne'),
    COALESCE(p.m_product_category_id, rs.m_product_category_id, 0),
    COALESCE(NULLIF(pc.name, ''), 'Categorie non renseignee'),
    COALESCE(NULLIF(th.name, ''), 'Thematique non renseignee'),
    COALESCE(vps.supplier_id, 0),
    COALESCE(NULLIF(vps.supplier_name, ''), 'Fournisseur non renseigne'),
    COALESCE(rs.m_warehouse_id, 0),
    COALESCE(NULLIF(w.name, ''), 'Depot non renseigne');

GRANT SELECT ON
    mart.mart_sales_by_commercial,
    mart.mart_sales_by_customer,
    mart.mart_sales_by_product,
    mart.mart_sales_by_region,
    mart.mart_order_to_invoice_flow,
    mart.mart_stock_risk
TO lpn_ai_readonly;
