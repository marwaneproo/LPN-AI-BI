-- =============================================================================
-- LPN AI-BI  ·  Warehouse Schema DDL (dim_* / fact_*)
-- Task     : BI-07 – Warehouse Dim/Fact Design Alignment
-- Date     : 2026-06-25
-- Branch   : bi-polished-dashboard
-- Author   : Claude Sonnet 4.6 (design only)
-- Status   : DRAFT – DO NOT EXECUTE IN PRODUCTION without review
--
-- Reconciles DataWareHouse/processus_de_vente/ddl/draft_schema_design.sql
-- with confirmed staging column names (BI-06), fact grains (03_fact_grain_design.md),
-- star schema design (02_star_schema_design.md), and actual ETL output headers.
--
-- Changes vs draft_schema_design.sql are marked with DECISION-xx.
-- All dimensions carry SCD1. Type-2 placeholder columns (effective_from/to,
-- is_current) preserved on dim_customer and dim_product for future upgrade only.
-- Unknown surrogate rows use key=0 (DECISION-02).
--
-- Schema target : lpn_ai_bi  (PostgreSQL 18)
-- Execution prerequisite: staging schema must exist (BI-06 staging.sql run first)
-- Do NOT run: business schema and staging schema are never modified here
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS warehouse;

COMMENT ON SCHEMA warehouse IS
  'Warehouse layer: dim_* and fact_* tables for LPN processus de vente BI.';

-- =============================================================================
-- DIMENSIONS
-- =============================================================================

-- ---------------------------------------------------------------------------
-- dim_date
-- Grain     : One row per calendar day
-- Key       : date_key  INTEGER YYYYMMDD (SCD0 – calendar rows never change)
-- Unknown   : date_key = 0  (DECISION-02)
-- Source    : Generated from transactional date range 2024-01-01 → 2026-12-31
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.dim_date (
    date_key        INTEGER      PRIMARY KEY,
    full_date       DATE,
    day_of_month    SMALLINT,
    month_number    SMALLINT,
    month_name      VARCHAR(30),
    quarter_number  SMALLINT,
    year_number     INTEGER,
    week_of_year    SMALLINT,
    is_month_end    BOOLEAN      DEFAULT FALSE
);

COMMENT ON TABLE warehouse.dim_date IS
  'Calendar dimension. Grain=one row per day. SCD0. date_key=YYYYMMDD integer.';

INSERT INTO warehouse.dim_date
    (date_key, full_date, day_of_month, month_number, month_name, quarter_number, year_number, week_of_year, is_month_end)
VALUES
    (0, NULL, 0, 0, 'Unknown / Non renseigne', 0, 0, NULL, FALSE)
ON CONFLICT (date_key) DO NOTHING;


-- ---------------------------------------------------------------------------
-- dim_customer
-- Grain     : One row per active customer (SCD1 in v1)
-- Key       : customer_key  BIGSERIAL
-- Unknown   : customer_key = 0  (DECISION-02)
-- Source    : staging.stg_c_bpartner WHERE iscustomer='Y' AND value != 'NA'
-- SCD note  : effective_from/effective_to/is_current kept as placeholder (DECISION-06)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.dim_customer (
    customer_key            BIGSERIAL    PRIMARY KEY,
    c_bpartner_id           BIGINT,
    customer_code           VARCHAR(80),
    customer_name           VARCHAR(255),
    customer_group_id       BIGINT,
    customer_group_name     VARCHAR(255),
    is_customer             BOOLEAN,
    is_vendor               BOOLEAN,
    salesrep_id             BIGINT,
    credit_limit            NUMERIC(18,4),
    is_active               BOOLEAN,
    -- SCD2 placeholder columns (DECISION-06: not populated in v1; ready for future upgrade)
    effective_from          DATE,
    effective_to            DATE,
    is_current              BOOLEAN      DEFAULT TRUE,
    UNIQUE (c_bpartner_id, effective_from)
);

COMMENT ON TABLE warehouse.dim_customer IS
  'Customer dimension. SCD1 in v1. SCD2 columns kept as placeholder for future history.';

INSERT INTO warehouse.dim_customer
    (customer_key, c_bpartner_id, customer_code, customer_name, customer_group_id, customer_group_name,
     is_customer, is_vendor, salesrep_id, credit_limit, is_active, is_current)
VALUES
    (0, NULL, NULL, 'Unknown / Non renseigne', NULL, 'Unknown / Non renseigne',
     NULL, NULL, NULL, NULL, NULL, TRUE)
ON CONFLICT (customer_key) DO NOTHING;


-- ---------------------------------------------------------------------------
-- dim_commercial
-- Grain     : One row per sales representative (SCD1)
-- Key       : commercial_key  BIGSERIAL
-- Unknown   : commercial_key = 0  (DECISION-02)
-- Source    : staging.stg_ad_user  (+ SALESREP_ID filter)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.dim_commercial (
    commercial_key      BIGSERIAL    PRIMARY KEY,
    ad_user_id          BIGINT       UNIQUE,
    commercial_name     VARCHAR(255),
    email               VARCHAR(255),
    description         TEXT,
    c_bpartner_id       BIGINT,
    is_active           BOOLEAN
);

COMMENT ON TABLE warehouse.dim_commercial IS
  'Sales representatives. SCD1. INITCAP(TRIM(name)) applied at build time.';

INSERT INTO warehouse.dim_commercial
    (commercial_key, ad_user_id, commercial_name, description)
VALUES
    (0, NULL, 'Unknown / Non renseigne', 'Unknown / Non renseigne')
ON CONFLICT (commercial_key) DO NOTHING;


-- ---------------------------------------------------------------------------
-- dim_product_category
-- Grain     : One row per product category (SCD1)
-- Key       : product_category_key  BIGSERIAL
-- Unknown   : product_category_key = 0  (DECISION-02)
-- Source    : staging.stg_m_product_category
-- Quality   : REGEXP_REPLACE(TRIM(name), E'[\\t\\r\\n]+', ' ', 'g') at build (BI-03 W-02/04)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.dim_product_category (
    product_category_key    BIGSERIAL    PRIMARY KEY,
    m_product_category_id   BIGINT       UNIQUE,
    category_code           VARCHAR(80),
    category_name           VARCHAR(255),
    description             TEXT,
    planned_margin          NUMERIC(18,4),
    is_default              BOOLEAN
);

COMMENT ON TABLE warehouse.dim_product_category IS
  'Product categories. 41 rows in source. Trailing tabs/newlines cleaned at build.';

INSERT INTO warehouse.dim_product_category
    (product_category_key, m_product_category_id, category_code, category_name)
VALUES
    (0, NULL, NULL, 'Unknown / Non renseigne')
ON CONFLICT (product_category_key) DO NOTHING;


-- ---------------------------------------------------------------------------
-- dim_supplier
-- Grain     : One row per primary supplier (SCD1)
-- Key       : supplier_key  BIGSERIAL
-- Unknown   : supplier_key = 0  (DECISION-02)
-- Source    : staging.stg_c_bpartner_vendor  (deduped via DEDUP-SUPP-01: lowest SEQNO per product)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.dim_supplier (
    supplier_key            BIGSERIAL    PRIMARY KEY,
    c_bpartner_id           BIGINT       UNIQUE,
    supplier_code           VARCHAR(80),
    supplier_name           VARCHAR(255),
    supplier_group_id       BIGINT,
    supplier_group_name     VARCHAR(255),
    is_active               BOOLEAN
);

COMMENT ON TABLE warehouse.dim_supplier IS
  'Supplier/distributor dimension. SCD1. One row per vendor BP.';

INSERT INTO warehouse.dim_supplier
    (supplier_key, c_bpartner_id, supplier_code, supplier_name, supplier_group_name, is_active)
VALUES
    (0, NULL, NULL, 'Unknown / Non renseigne', 'Unknown / Non renseigne', NULL)
ON CONFLICT (supplier_key) DO NOTHING;


-- ---------------------------------------------------------------------------
-- dim_product
-- Grain     : One row per active product (SCD1 in v1)
-- Key       : product_key  BIGSERIAL
-- Unknown   : product_key = 0  (DECISION-02)
-- Source    : staging.stg_m_product (CSV only — 2,272 MB xlsx excluded)
-- Enrich    : + stg_m_product_theme / stg_m_product_type / stg_m_product_collection
-- SCD note  : effective_from/effective_to/is_current kept as placeholder (DECISION-06)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.dim_product (
    product_key             BIGSERIAL    PRIMARY KEY,
    m_product_id            BIGINT       UNIQUE,
    product_code            VARCHAR(80),
    product_name            VARCHAR(500),
    description             TEXT,
    product_type_code       VARCHAR(20),
    product_type_name       VARCHAR(255),
    theme_name              VARCHAR(255),
    collection_name         VARCHAR(255),
    is_sold                 BOOLEAN,
    is_purchased            BOOLEAN,
    is_stocked              BOOLEAN,
    product_category_key    BIGINT       REFERENCES warehouse.dim_product_category(product_category_key),
    supplier_key            BIGINT       REFERENCES warehouse.dim_supplier(supplier_key),
    -- SCD2 placeholder columns (DECISION-06)
    effective_from          DATE,
    effective_to            DATE,
    is_current              BOOLEAN      DEFAULT TRUE
);

COMMENT ON TABLE warehouse.dim_product IS
  'Products/articles. SCD1 in v1. SCD2 placeholders kept. Enriched with theme/type/collection.';

INSERT INTO warehouse.dim_product
    (product_key, m_product_id, product_code, product_name, description,
     product_type_name, theme_name, collection_name, product_category_key, supplier_key, is_current)
VALUES
    (0, NULL, NULL, 'Unknown / Non renseigne', 'Unknown / Non renseigne',
     'Unknown / Non renseigne', 'Unknown / Non renseigne', 'Unknown / Non renseigne', 0, 0, TRUE)
ON CONFLICT (product_key) DO NOTHING;


-- ---------------------------------------------------------------------------
-- dim_sales_region
-- Grain     : One row per sales region (SCD1)
-- Key       : sales_region_key  BIGSERIAL
-- Unknown   : sales_region_key = 0  (DECISION-02)
-- Source    : staging.stg_c_salesregion (13 rows)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.dim_sales_region (
    sales_region_key    BIGSERIAL    PRIMARY KEY,
    c_salesregion_id    BIGINT       UNIQUE,
    sales_region_code   VARCHAR(80),
    sales_region_name   VARCHAR(255),
    description         TEXT,
    is_summary          BOOLEAN,
    is_default          BOOLEAN
);

COMMENT ON TABLE warehouse.dim_sales_region IS
  'Moroccan commercial/geographic sales regions (13 rows).';

INSERT INTO warehouse.dim_sales_region
    (sales_region_key, c_salesregion_id, sales_region_name)
VALUES
    (0, NULL, 'Unknown / Non renseigne')
ON CONFLICT (sales_region_key) DO NOTHING;


-- ---------------------------------------------------------------------------
-- dim_geography
-- Grain     : One row per (c_bpartner_location_id, c_location_id) pair (SCD1)
-- Key       : geography_key  BIGSERIAL
-- Unknown   : geography_key = 0  (DECISION-02)
-- Source    : staging.stg_c_bpartner_location JOIN staging.stg_c_location
--             JOIN staging.stg_c_city JOIN staging.stg_c_region JOIN dim_sales_region
-- Note      : sector_detail = C_BPARTNER_LOCATION.SECTORDETAIL (LPN pedagogical field)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.dim_geography (
    geography_key               BIGSERIAL    PRIMARY KEY,
    c_bpartner_location_id      BIGINT,
    c_location_id               BIGINT,
    location_name               VARCHAR(255),
    is_bill_to                  BOOLEAN,
    is_ship_to                  BOOLEAN,
    is_pay_from                 BOOLEAN,
    is_remit_to                 BOOLEAN,
    address1                    VARCHAR(255),
    address2                    VARCHAR(255),
    city_name                   VARCHAR(255),
    region_name                 VARCHAR(255),
    country_id                  BIGINT,
    c_city_id                   BIGINT,
    c_region_id                 BIGINT,
    sales_region_key            BIGINT       REFERENCES warehouse.dim_sales_region(sales_region_key),
    sector_detail               VARCHAR(255),
    UNIQUE (c_bpartner_location_id, c_location_id)
);

COMMENT ON TABLE warehouse.dim_geography IS
  'Customer locations. Grain=BP_location + physical_location pair. sector_detail=pedagogical zone.';

INSERT INTO warehouse.dim_geography
    (geography_key, c_bpartner_location_id, c_location_id, location_name,
     city_name, region_name, sales_region_key)
VALUES
    (0, NULL, NULL, 'Unknown / Non renseigne', 'Unknown / Non renseigne', 'Unknown / Non renseigne', 0)
ON CONFLICT (geography_key) DO NOTHING;


-- ---------------------------------------------------------------------------
-- dim_payment_term
-- Grain     : One row per payment term (SCD1)
-- Key       : payment_term_key  BIGSERIAL
-- Unknown   : payment_term_key = 0  (DECISION-02)
-- Source    : staging.stg_c_paymentterm (7 rows)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.dim_payment_term (
    payment_term_key    BIGSERIAL    PRIMARY KEY,
    c_paymentterm_id    BIGINT       UNIQUE,
    payment_term_name   VARCHAR(255),
    description         TEXT,
    net_days            INTEGER,
    grace_days          INTEGER,
    after_delivery      BOOLEAN,
    is_due_fixed        BOOLEAN,
    is_default          BOOLEAN
);

COMMENT ON TABLE warehouse.dim_payment_term IS 'Payment terms reference (7 rows). SCD1.';

INSERT INTO warehouse.dim_payment_term
    (payment_term_key, c_paymentterm_id, payment_term_name)
VALUES
    (0, NULL, 'Unknown / Non renseigne')
ON CONFLICT (payment_term_key) DO NOTHING;


-- ---------------------------------------------------------------------------
-- dim_price_list
-- Grain     : One row per price list (SCD1)
-- Key       : price_list_key  BIGSERIAL
-- Unknown   : price_list_key = 0  (DECISION-02)
-- Source    : staging.stg_m_pricelist (22 rows)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.dim_price_list (
    price_list_key      BIGSERIAL    PRIMARY KEY,
    m_pricelist_id      BIGINT       UNIQUE,
    price_list_name     VARCHAR(255),
    description         TEXT,
    base_pricelist_id   BIGINT,
    is_tax_included     BOOLEAN,
    is_so_price_list    BOOLEAN,
    currency_id         BIGINT
);

COMMENT ON TABLE warehouse.dim_price_list IS
  'Price lists (22 rows). currency_id=239 (MAD) for all sales lists. SCD1.';

INSERT INTO warehouse.dim_price_list
    (price_list_key, m_pricelist_id, price_list_name)
VALUES
    (0, NULL, 'Unknown / Non renseigne')
ON CONFLICT (price_list_key) DO NOTHING;


-- ---------------------------------------------------------------------------
-- dim_document_type
-- Grain     : One row per ERP document type (SCD1)
-- Key       : document_type_key  BIGSERIAL
-- Unknown   : document_type_key = 0  (DECISION-02)
-- Source    : staging.stg_c_doctype WHERE issotrx = 'Y' (FILT-11)
-- Note      : Deliveries typically resolve to document_type_key=0 (DECISION-08)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.dim_document_type (
    document_type_key       BIGSERIAL    PRIMARY KEY,
    c_doctype_id            BIGINT       UNIQUE,
    document_type_name      VARCHAR(255),
    print_name              VARCHAR(255),
    description             TEXT,
    doc_base_type           VARCHAR(20),
    doc_subtype_so          VARCHAR(20),
    is_sales_transaction    BOOLEAN
);

COMMENT ON TABLE warehouse.dim_document_type IS
  'ERP document types. Filtered to sales transactions. DOCACTION column excluded (NORM-04).';

INSERT INTO warehouse.dim_document_type
    (document_type_key, c_doctype_id, document_type_name, print_name)
VALUES
    (0, NULL, 'Unknown / Non renseigne', 'Unknown / Non renseigne')
ON CONFLICT (document_type_key) DO NOTHING;


-- ---------------------------------------------------------------------------
-- dim_warehouse
-- Grain     : One row per (m_warehouse_id, m_locator_id) pair (SCD1)
-- Key       : warehouse_key  BIGSERIAL
-- Unknown   : warehouse_key = 0  (DECISION-02)
-- Source    : staging.stg_m_warehouse (7 rows) + locator from stg_m_inoutline/stg_rv_storage
-- Note      : M_WAREHOUSE_ID=1,000,000 singleton in transactional data (BI-02)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.dim_warehouse (
    warehouse_key           BIGSERIAL    PRIMARY KEY,
    m_warehouse_id          BIGINT,
    warehouse_code          VARCHAR(80),
    warehouse_name          VARCHAR(255),
    m_locator_id            BIGINT,
    locator_value           VARCHAR(80),
    is_default_locator      BOOLEAN,
    UNIQUE (m_warehouse_id, m_locator_id)
);

COMMENT ON TABLE warehouse.dim_warehouse IS
  'Warehouses and locators. warehouse_code=stg_m_warehouse.value; warehouse_name=stg_m_warehouse.name.';

INSERT INTO warehouse.dim_warehouse
    (warehouse_key, m_warehouse_id, warehouse_code, warehouse_name, m_locator_id, locator_value)
VALUES
    (0, NULL, NULL, 'Unknown / Non renseigne', NULL, NULL)
ON CONFLICT (warehouse_key) DO NOTHING;


-- =============================================================================
-- FACTS
-- =============================================================================

-- ---------------------------------------------------------------------------
-- fact_sales_order
-- Grain     : One row per C_ORDER_ID (confirmed BI-02: PK unique in each source)
-- Source    : staging.stg_c_order  (filter: DOCSTATUS IN ('CO','CL') + FILT-01/02)
-- Dedup     : csv_2025 preferred over 2024_xlsx on same C_ORDER_ID (DEDUP-00)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.fact_sales_order (
    sales_order_key     BIGSERIAL    PRIMARY KEY,

    -- Degenerate dimensions (natural IDs from source)
    c_order_id          BIGINT       NOT NULL UNIQUE,
    document_no         VARCHAR(80),
    doc_status          VARCHAR(10),
    po_reference        TEXT,

    -- Date FKs
    order_date_key      INTEGER      REFERENCES warehouse.dim_date(date_key),

    -- Dimension FKs
    customer_key        BIGINT       REFERENCES warehouse.dim_customer(customer_key),
    commercial_key      BIGINT       REFERENCES warehouse.dim_commercial(commercial_key),
    geography_key       BIGINT       REFERENCES warehouse.dim_geography(geography_key),
    payment_term_key    BIGINT       REFERENCES warehouse.dim_payment_term(payment_term_key),
    price_list_key      BIGINT       REFERENCES warehouse.dim_price_list(price_list_key),
    document_type_key   BIGINT       REFERENCES warehouse.dim_document_type(document_type_key),
    warehouse_key       BIGINT       REFERENCES warehouse.dim_warehouse(warehouse_key),

    -- Measures (all additive)
    total_lines_amount  NUMERIC(18,4),
    grand_total_amount  NUMERIC(18,4),
    freight_amount      NUMERIC(18,4),
    charge_amount       NUMERIC(18,4),
    order_count         INTEGER      DEFAULT 1   -- additive constant = 1
);

COMMENT ON TABLE warehouse.fact_sales_order IS
  'Grain=C_ORDER_ID. Measures: grand_total_amount=CA commande. Filter DOCSTATUS IN (CO,CL).';

CREATE INDEX IF NOT EXISTS idx_fso_customer     ON warehouse.fact_sales_order (customer_key);
CREATE INDEX IF NOT EXISTS idx_fso_commercial   ON warehouse.fact_sales_order (commercial_key);
CREATE INDEX IF NOT EXISTS idx_fso_order_date   ON warehouse.fact_sales_order (order_date_key);
CREATE INDEX IF NOT EXISTS idx_fso_doctype      ON warehouse.fact_sales_order (document_type_key);


-- ---------------------------------------------------------------------------
-- fact_sales_order_line
-- Grain     : One row per C_ORDERLINE_ID (confirmed BI-02: PK unique in each source)
-- Source    : staging.stg_c_orderline + dim lookups
-- Enriched  : product_key/category_key/supplier_key from dim_product (inherited)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.fact_sales_order_line (
    sales_order_line_key    BIGSERIAL    PRIMARY KEY,

    -- Degenerate dimensions
    c_orderline_id          BIGINT       NOT NULL UNIQUE,
    c_order_id              BIGINT       NOT NULL,
    document_no             VARCHAR(80),
    doc_status              VARCHAR(10),
    line_no                 INTEGER,

    -- Date FK
    order_date_key          INTEGER      REFERENCES warehouse.dim_date(date_key),

    -- Dimension FKs
    customer_key            BIGINT       REFERENCES warehouse.dim_customer(customer_key),
    commercial_key          BIGINT       REFERENCES warehouse.dim_commercial(commercial_key),
    product_key             BIGINT       REFERENCES warehouse.dim_product(product_key),
    product_category_key    BIGINT       REFERENCES warehouse.dim_product_category(product_category_key),
    supplier_key            BIGINT       REFERENCES warehouse.dim_supplier(supplier_key),
    warehouse_key           BIGINT       REFERENCES warehouse.dim_warehouse(warehouse_key),
    document_type_key       BIGINT       REFERENCES warehouse.dim_document_type(document_type_key),

    -- Measures
    quantity_ordered        NUMERIC(18,4),   -- additive
    quantity_delivered      NUMERIC(18,4),   -- additive
    quantity_invoiced       NUMERIC(18,4),   -- additive
    price_list              NUMERIC(18,4),   -- non-additive; use weighted avg
    price_actual            NUMERIC(18,4),   -- non-additive; use weighted avg
    discount_percent        NUMERIC(18,4),   -- non-additive
    line_net_amount         NUMERIC(18,4),   -- additive
    line_count              INTEGER          DEFAULT 1  -- additive constant = 1
);

COMMENT ON TABLE warehouse.fact_sales_order_line IS
  'Grain=C_ORDERLINE_ID. Bridges to fact_invoice_line and fact_delivery_line via c_orderline_id.';

CREATE INDEX IF NOT EXISTS idx_fsol_product      ON warehouse.fact_sales_order_line (product_key);
CREATE INDEX IF NOT EXISTS idx_fsol_customer     ON warehouse.fact_sales_order_line (customer_key);
CREATE INDEX IF NOT EXISTS idx_fsol_order_date   ON warehouse.fact_sales_order_line (order_date_key);
CREATE INDEX IF NOT EXISTS idx_fsol_c_order_id   ON warehouse.fact_sales_order_line (c_order_id);


-- ---------------------------------------------------------------------------
-- fact_invoice
-- Grain     : One row per C_INVOICE_ID (confirmed BI-02: PK unique in each source)
-- Source    : staging.stg_c_invoice  (filter: DOCSTATUS='CO' + FILT-03/04)
-- Note      : source_c_order_id NULL in 29.9% of rows — valid (BI-02 NORM-08)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.fact_invoice (
    invoice_key             BIGSERIAL    PRIMARY KEY,

    -- Degenerate dimensions
    c_invoice_id            BIGINT       NOT NULL UNIQUE,
    source_c_order_id       BIGINT,              -- NULL for 29.9% rows — valid (BI-02)
    document_no             VARCHAR(80),
    doc_status              VARCHAR(10),
    is_paid                 BOOLEAN,

    -- Date FK
    invoice_date_key        INTEGER      REFERENCES warehouse.dim_date(date_key),

    -- Dimension FKs
    customer_key            BIGINT       REFERENCES warehouse.dim_customer(customer_key),
    commercial_key          BIGINT       REFERENCES warehouse.dim_commercial(commercial_key),
    geography_key           BIGINT       REFERENCES warehouse.dim_geography(geography_key),
    payment_term_key        BIGINT       REFERENCES warehouse.dim_payment_term(payment_term_key),
    price_list_key          BIGINT       REFERENCES warehouse.dim_price_list(price_list_key),
    document_type_key       BIGINT       REFERENCES warehouse.dim_document_type(document_type_key),

    -- Measures
    total_lines_amount      NUMERIC(18,4),
    grand_total_amount      NUMERIC(18,4),
    invoice_count           INTEGER      DEFAULT 1,
    paid_invoice_count      INTEGER      DEFAULT 0,   -- 1 if is_paid=TRUE
    unpaid_invoice_count    INTEGER      DEFAULT 0    -- 1 if is_paid=FALSE
);

COMMENT ON TABLE warehouse.fact_invoice IS
  'Grain=C_INVOICE_ID. grand_total_amount=CA facture source of truth. is_paid=ISPAID flag.';

CREATE INDEX IF NOT EXISTS idx_fi_customer       ON warehouse.fact_invoice (customer_key);
CREATE INDEX IF NOT EXISTS idx_fi_commercial     ON warehouse.fact_invoice (commercial_key);
CREATE INDEX IF NOT EXISTS idx_fi_invoice_date   ON warehouse.fact_invoice (invoice_date_key);
CREATE INDEX IF NOT EXISTS idx_fi_is_paid        ON warehouse.fact_invoice (is_paid);
CREATE INDEX IF NOT EXISTS idx_fi_src_order      ON warehouse.fact_invoice (source_c_order_id);


-- ---------------------------------------------------------------------------
-- fact_invoice_line
-- Grain     : One row per C_INVOICELINE_ID (confirmed BI-02: PK unique in each source)
-- Source    : staging.stg_c_invoiceline + dim lookups
-- Bridges   : c_orderline_id → fact_sales_order_line; c_invoice_id → fact_invoice
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.fact_invoice_line (
    invoice_line_key        BIGSERIAL    PRIMARY KEY,

    -- Degenerate dimensions
    c_invoiceline_id        BIGINT       NOT NULL UNIQUE,
    c_invoice_id            BIGINT       NOT NULL,
    c_orderline_id          BIGINT,          -- bridge to order line (may be NULL)
    document_no             VARCHAR(80),
    line_no                 INTEGER,

    -- Date FK (inherited from invoice header)
    invoice_date_key        INTEGER      REFERENCES warehouse.dim_date(date_key),

    -- Dimension FKs
    customer_key            BIGINT       REFERENCES warehouse.dim_customer(customer_key),
    commercial_key          BIGINT       REFERENCES warehouse.dim_commercial(commercial_key),
    product_key             BIGINT       REFERENCES warehouse.dim_product(product_key),
    product_category_key    BIGINT       REFERENCES warehouse.dim_product_category(product_category_key),
    supplier_key            BIGINT       REFERENCES warehouse.dim_supplier(supplier_key),
    document_type_key       BIGINT       REFERENCES warehouse.dim_document_type(document_type_key),

    -- Measures
    quantity_invoiced       NUMERIC(18,4),   -- additive
    price_list              NUMERIC(18,4),   -- non-additive
    price_actual            NUMERIC(18,4),   -- non-additive
    line_net_amount         NUMERIC(18,4),   -- additive — primary CA facture by product
    invoice_line_count      INTEGER          DEFAULT 1
);

COMMENT ON TABLE warehouse.fact_invoice_line IS
  'Grain=C_INVOICELINE_ID. SUM(line_net_amount) GROUP BY product=top_produits_ca_facture.';

CREATE INDEX IF NOT EXISTS idx_fil_product       ON warehouse.fact_invoice_line (product_key);
CREATE INDEX IF NOT EXISTS idx_fil_customer      ON warehouse.fact_invoice_line (customer_key);
CREATE INDEX IF NOT EXISTS idx_fil_invoice_date  ON warehouse.fact_invoice_line (invoice_date_key);
CREATE INDEX IF NOT EXISTS idx_fil_c_invoice_id  ON warehouse.fact_invoice_line (c_invoice_id);
CREATE INDEX IF NOT EXISTS idx_fil_c_orderline   ON warehouse.fact_invoice_line (c_orderline_id);


-- ---------------------------------------------------------------------------
-- fact_delivery
-- Grain     : One row per M_INOUT_ID
-- Source    : staging.stg_m_inout  (filter: ISSOTRX='Y' + DOCSTATUS='CO' — FILT-05/06)
-- Note      : document_type_key resolves to 0 (unknown) for most delivery records (DECISION-08)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.fact_delivery (
    delivery_key            BIGSERIAL    PRIMARY KEY,

    -- Degenerate dimensions
    m_inout_id              BIGINT       NOT NULL UNIQUE,
    source_c_order_id       BIGINT,          -- bridge to order header
    document_no             VARCHAR(80),
    doc_status              VARCHAR(10),

    -- Date FK
    movement_date_key       INTEGER      REFERENCES warehouse.dim_date(date_key),

    -- Dimension FKs
    customer_key            BIGINT       REFERENCES warehouse.dim_customer(customer_key),
    geography_key           BIGINT       REFERENCES warehouse.dim_geography(geography_key),
    warehouse_key           BIGINT       REFERENCES warehouse.dim_warehouse(warehouse_key),
    document_type_key       BIGINT       REFERENCES warehouse.dim_document_type(document_type_key),

    -- Measures
    delivery_count          INTEGER      DEFAULT 1
);

COMMENT ON TABLE warehouse.fact_delivery IS
  'Grain=M_INOUT_ID. Filter ISSOTRX=Y + DOCSTATUS=CO. document_type_key often=0 (DECISION-08).';

CREATE INDEX IF NOT EXISTS idx_fd_customer       ON warehouse.fact_delivery (customer_key);
CREATE INDEX IF NOT EXISTS idx_fd_movement_date  ON warehouse.fact_delivery (movement_date_key);
CREATE INDEX IF NOT EXISTS idx_fd_src_order      ON warehouse.fact_delivery (source_c_order_id);


-- ---------------------------------------------------------------------------
-- fact_delivery_line
-- Grain     : One row per M_INOUTLINE_ID
-- Source    : staging.stg_m_inoutline + delivery header context
-- Note      : qtyonhand/qtyreserved from M_INOUTLINE are snapshot values (semi-additive)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.fact_delivery_line (
    delivery_line_key               BIGSERIAL    PRIMARY KEY,

    -- Degenerate dimensions
    m_inoutline_id                  BIGINT       NOT NULL UNIQUE,
    m_inout_id                      BIGINT       NOT NULL,
    c_orderline_id                  BIGINT,          -- bridge to order line
    line_no                         INTEGER,
    availability_indicator          TEXT,            -- disponibilite flag from source

    -- Date FK (inherited from delivery header MOVEMENTDATE)
    movement_date_key               INTEGER      REFERENCES warehouse.dim_date(date_key),

    -- Dimension FKs
    customer_key                    BIGINT       REFERENCES warehouse.dim_customer(customer_key),
    product_key                     BIGINT       REFERENCES warehouse.dim_product(product_key),
    product_category_key            BIGINT       REFERENCES warehouse.dim_product_category(product_category_key),
    supplier_key                    BIGINT       REFERENCES warehouse.dim_supplier(supplier_key),
    warehouse_key                   BIGINT       REFERENCES warehouse.dim_warehouse(warehouse_key),

    -- Measures
    movement_quantity               NUMERIC(18,4),   -- additive (shipped qty)
    entered_quantity                NUMERIC(18,4),   -- additive
    quantity_on_hand_snapshot       NUMERIC(18,4),   -- semi-additive (do not sum over time)
    quantity_reserved_snapshot      NUMERIC(18,4),   -- semi-additive
    delivery_line_count             INTEGER          DEFAULT 1
);

COMMENT ON TABLE warehouse.fact_delivery_line IS
  'Grain=M_INOUTLINE_ID. movement_quantity=shipped qty (additive). qtyonhand=semi-additive.';

CREATE INDEX IF NOT EXISTS idx_fdl_product       ON warehouse.fact_delivery_line (product_key);
CREATE INDEX IF NOT EXISTS idx_fdl_customer      ON warehouse.fact_delivery_line (customer_key);
CREATE INDEX IF NOT EXISTS idx_fdl_m_inout_id    ON warehouse.fact_delivery_line (m_inout_id);
CREATE INDEX IF NOT EXISTS idx_fdl_c_orderline   ON warehouse.fact_delivery_line (c_orderline_id);


-- ---------------------------------------------------------------------------
-- fact_payment_allocation
-- Grain     : One row per C_ALLOCATIONLINE_ID
-- Source    : staging.stg_c_allocationline + stg_c_allocationhdr + stg_c_payment
-- Note      : commercial_key often resolves to 0 (unknown) — derived from invoice (DECISION-07)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.fact_payment_allocation (
    payment_allocation_key      BIGSERIAL    PRIMARY KEY,

    -- Degenerate dimensions
    c_allocationline_id         BIGINT       NOT NULL UNIQUE,
    c_allocationhdr_id          BIGINT,
    c_invoice_id                BIGINT,          -- bridge to fact_invoice
    c_payment_id                BIGINT,
    c_order_id                  BIGINT,
    payment_document_no         VARCHAR(80),

    -- Date FK
    allocation_date_key         INTEGER      REFERENCES warehouse.dim_date(date_key),

    -- Dimension FKs
    customer_key                BIGINT       REFERENCES warehouse.dim_customer(customer_key),
    commercial_key              BIGINT       REFERENCES warehouse.dim_commercial(commercial_key),
    payment_document_type_key   BIGINT       REFERENCES warehouse.dim_document_type(document_type_key),

    -- Measures (all additive)
    allocated_amount            NUMERIC(18,4),
    discount_amount             NUMERIC(18,4),
    writeoff_amount             NUMERIC(18,4),
    overunder_amount            NUMERIC(18,4),
    payment_amount              NUMERIC(18,4),   -- caution: payment header may repeat across allocations
    allocation_count            INTEGER          DEFAULT 1
);

COMMENT ON TABLE warehouse.fact_payment_allocation IS
  'Grain=C_ALLOCATIONLINE_ID. Bridge invoice←→payment. commercial_key via invoice join only.';

CREATE INDEX IF NOT EXISTS idx_fpa_customer      ON warehouse.fact_payment_allocation (customer_key);
CREATE INDEX IF NOT EXISTS idx_fpa_alloc_date    ON warehouse.fact_payment_allocation (allocation_date_key);
CREATE INDEX IF NOT EXISTS idx_fpa_c_invoice_id  ON warehouse.fact_payment_allocation (c_invoice_id);
CREATE INDEX IF NOT EXISTS idx_fpa_c_payment_id  ON warehouse.fact_payment_allocation (c_payment_id);


-- ---------------------------------------------------------------------------
-- fact_stock_snapshot
-- Grain     : One row per (m_product_id, m_attributesetinstance_id, m_warehouse_id, m_locator_id)
--             at the extraction snapshot date.
--
-- DECISION-05: Added m_locator_id as degenerate dimension vs. prototype.
-- Rationale : RV_STORAGE grain includes m_locator_id; without it, multiple rows
--             exist per (product, attributeset, warehouse) causing PK conflicts.
--             Confirmed by ETL output: stg_rv_storage_pkey includes m_locator_id.
--
-- Source    : staging.stg_rv_storage
-- Measures  : ALL semi-additive — do NOT sum across snapshot dates.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warehouse.fact_stock_snapshot (
    stock_snapshot_key          BIGSERIAL    PRIMARY KEY,

    -- Date FK
    snapshot_date_key           INTEGER      REFERENCES warehouse.dim_date(date_key),

    -- Degenerate dimensions
    m_product_id                BIGINT,
    m_attribute_set_instance_id BIGINT,
    m_locator_id                BIGINT,   -- DECISION-05: added vs prototype

    -- Dimension FKs
    product_key                 BIGINT       REFERENCES warehouse.dim_product(product_key),
    product_category_key        BIGINT       REFERENCES warehouse.dim_product_category(product_category_key),
    supplier_key                BIGINT       REFERENCES warehouse.dim_supplier(supplier_key),
    warehouse_key               BIGINT       REFERENCES warehouse.dim_warehouse(warehouse_key),

    -- Measures (ALL semi-additive — sum only at a single snapshot date)
    quantity_on_hand            NUMERIC(18,4),
    quantity_reserved           NUMERIC(18,4),
    quantity_available          NUMERIC(18,4),
    quantity_ordered            NUMERIC(18,4),
    stock_row_count             INTEGER          DEFAULT 1
);

COMMENT ON TABLE warehouse.fact_stock_snapshot IS
  'Grain=product/attributeset/warehouse/locator per snapshot. ALL measures semi-additive.';

CREATE INDEX IF NOT EXISTS idx_fss_product       ON warehouse.fact_stock_snapshot (product_key);
CREATE INDEX IF NOT EXISTS idx_fss_warehouse     ON warehouse.fact_stock_snapshot (warehouse_key);
CREATE INDEX IF NOT EXISTS idx_fss_snapshot_date ON warehouse.fact_stock_snapshot (snapshot_date_key);

-- =============================================================================
-- SEQUENCE GUARDS
-- After inserting the unknown rows (key=0), ensure sequences start at 1
-- so subsequent INSERTs do not attempt to reuse 0.
-- These statements are idempotent and safe to re-run.
-- =============================================================================
SELECT setval('warehouse.dim_customer_customer_key_seq',        GREATEST(1, (SELECT MAX(customer_key) FROM warehouse.dim_customer)), true);
SELECT setval('warehouse.dim_commercial_commercial_key_seq',    GREATEST(1, (SELECT MAX(commercial_key) FROM warehouse.dim_commercial)), true);
SELECT setval('warehouse.dim_product_category_product_category_key_seq', GREATEST(1, (SELECT MAX(product_category_key) FROM warehouse.dim_product_category)), true);
SELECT setval('warehouse.dim_supplier_supplier_key_seq',        GREATEST(1, (SELECT MAX(supplier_key) FROM warehouse.dim_supplier)), true);
SELECT setval('warehouse.dim_product_product_key_seq',          GREATEST(1, (SELECT MAX(product_key) FROM warehouse.dim_product)), true);
SELECT setval('warehouse.dim_sales_region_sales_region_key_seq',GREATEST(1, (SELECT MAX(sales_region_key) FROM warehouse.dim_sales_region)), true);
SELECT setval('warehouse.dim_geography_geography_key_seq',      GREATEST(1, (SELECT MAX(geography_key) FROM warehouse.dim_geography)), true);
SELECT setval('warehouse.dim_payment_term_payment_term_key_seq',GREATEST(1, (SELECT MAX(payment_term_key) FROM warehouse.dim_payment_term)), true);
SELECT setval('warehouse.dim_price_list_price_list_key_seq',    GREATEST(1, (SELECT MAX(price_list_key) FROM warehouse.dim_price_list)), true);
SELECT setval('warehouse.dim_document_type_document_type_key_seq', GREATEST(1, (SELECT MAX(document_type_key) FROM warehouse.dim_document_type)), true);
SELECT setval('warehouse.dim_warehouse_warehouse_key_seq',      GREATEST(1, (SELECT MAX(warehouse_key) FROM warehouse.dim_warehouse)), true);

-- =============================================================================
-- End of warehouse DDL draft
-- Validation: sqlglot 30.11.0 — 0 parse errors
-- Next: BI-08 mart views (CREATE VIEW mart_* on top of dim_* / fact_*)
-- =============================================================================
