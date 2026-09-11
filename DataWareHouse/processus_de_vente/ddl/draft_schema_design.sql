-- Draft schema design for LPN Processus de Vente Data Warehouse.
-- This is a design draft, not a final implementation script.
-- Step 1 through Step 4 prototype: layer design, star schema design,
-- fact-grain definition, and CSV ETL outputs are now documented.
-- This file remains a draft DDL, not a production deployment migration.

CREATE SCHEMA IF NOT EXISTS dwh_vente;

-- ============================================================
-- Dimension tables
-- ============================================================

CREATE TABLE dwh_vente.dim_date (
    date_key              integer PRIMARY KEY,
    full_date             date NOT NULL UNIQUE,
    day_of_month          smallint NOT NULL,
    month_number          smallint NOT NULL,
    month_name            varchar(20) NOT NULL,
    quarter_number        smallint NOT NULL,
    year_number           integer NOT NULL,
    week_of_year          smallint,
    is_month_end          boolean DEFAULT false
);

CREATE TABLE dwh_vente.dim_customer (
    customer_key          bigserial PRIMARY KEY,
    c_bpartner_id         bigint NOT NULL,
    customer_code         varchar(80),
    customer_name         varchar(255),
    customer_group_id     bigint,
    customer_group_name   varchar(255),
    is_customer           boolean,
    is_vendor             boolean,
    salesrep_id           bigint,
    credit_limit          numeric(18, 4),
    is_active             boolean,
    effective_from        date,
    effective_to          date,
    is_current            boolean DEFAULT true,
    UNIQUE (c_bpartner_id, effective_from)
);

CREATE TABLE dwh_vente.dim_commercial (
    commercial_key        bigserial PRIMARY KEY,
    ad_user_id            bigint NOT NULL UNIQUE,
    commercial_name       varchar(255),
    email                 varchar(255),
    description           text,
    c_bpartner_id         bigint,
    is_active             boolean
);

CREATE TABLE dwh_vente.dim_product_category (
    product_category_key  bigserial PRIMARY KEY,
    m_product_category_id bigint NOT NULL UNIQUE,
    category_code         varchar(80),
    category_name         varchar(255),
    description           text,
    planned_margin        numeric(18, 4),
    is_default            boolean
);

CREATE TABLE dwh_vente.dim_supplier (
    supplier_key          bigserial PRIMARY KEY,
    c_bpartner_id         bigint NOT NULL UNIQUE,
    supplier_code         varchar(80),
    supplier_name         varchar(255),
    supplier_group_id     bigint,
    supplier_group_name   varchar(255),
    is_active             boolean
);

CREATE TABLE dwh_vente.dim_product (
    product_key           bigserial PRIMARY KEY,
    m_product_id          bigint NOT NULL UNIQUE,
    product_code          varchar(80),
    product_name          varchar(500),
    description           text,
    product_type_code     varchar(20),
    product_type_name     varchar(255),
    theme_name            varchar(255),
    collection_name       varchar(255),
    is_sold               boolean,
    is_purchased          boolean,
    is_stocked            boolean,
    product_category_key  bigint REFERENCES dwh_vente.dim_product_category(product_category_key),
    supplier_key          bigint REFERENCES dwh_vente.dim_supplier(supplier_key),
    effective_from        date,
    effective_to          date,
    is_current            boolean DEFAULT true
);

CREATE TABLE dwh_vente.dim_sales_region (
    sales_region_key      bigserial PRIMARY KEY,
    c_salesregion_id      bigint NOT NULL UNIQUE,
    sales_region_code     varchar(80),
    sales_region_name     varchar(255),
    description           text,
    is_summary            boolean,
    is_default            boolean
);

CREATE TABLE dwh_vente.dim_geography (
    geography_key              bigserial PRIMARY KEY,
    c_bpartner_location_id     bigint,
    c_location_id              bigint,
    location_name              varchar(255),
    is_bill_to                 boolean,
    is_ship_to                 boolean,
    is_pay_from                boolean,
    is_remit_to                boolean,
    address1                   varchar(255),
    address2                   varchar(255),
    city_name                  varchar(255),
    region_name                varchar(255),
    country_id                 bigint,
    c_city_id                  bigint,
    c_region_id                bigint,
    sales_region_key           bigint REFERENCES dwh_vente.dim_sales_region(sales_region_key),
    sector_detail              varchar(255),
    UNIQUE (c_bpartner_location_id, c_location_id)
);

CREATE TABLE dwh_vente.dim_payment_term (
    payment_term_key      bigserial PRIMARY KEY,
    c_paymentterm_id      bigint NOT NULL UNIQUE,
    payment_term_name     varchar(255),
    description           text,
    net_days              integer,
    grace_days            integer,
    after_delivery        boolean,
    is_due_fixed          boolean,
    is_default            boolean
);

CREATE TABLE dwh_vente.dim_price_list (
    price_list_key        bigserial PRIMARY KEY,
    m_pricelist_id        bigint NOT NULL UNIQUE,
    price_list_name       varchar(255),
    description           text,
    base_pricelist_id     bigint,
    is_tax_included       boolean,
    is_so_price_list      boolean,
    currency_id           bigint
);

CREATE TABLE dwh_vente.dim_document_type (
    document_type_key     bigserial PRIMARY KEY,
    c_doctype_id          bigint NOT NULL UNIQUE,
    document_type_name    varchar(255),
    print_name            varchar(255),
    description           text,
    doc_base_type         varchar(20),
    doc_subtype_so        varchar(20),
    is_sales_transaction  boolean
);

CREATE TABLE dwh_vente.dim_warehouse (
    warehouse_key         bigserial PRIMARY KEY,
    m_warehouse_id        bigint,
    warehouse_code        varchar(80),
    warehouse_name        varchar(255),
    m_locator_id          bigint,
    locator_value         varchar(80),
    is_default_locator    boolean,
    UNIQUE (m_warehouse_id, m_locator_id)
);

-- ============================================================
-- Fact tables
-- ============================================================

CREATE TABLE dwh_vente.fact_sales_order (
    sales_order_key       bigserial PRIMARY KEY,
    c_order_id            bigint NOT NULL UNIQUE,
    document_no           varchar(80),
    doc_status            varchar(10),
    po_reference          varchar(255),
    order_date_key        integer REFERENCES dwh_vente.dim_date(date_key),
    customer_key          bigint REFERENCES dwh_vente.dim_customer(customer_key),
    commercial_key        bigint REFERENCES dwh_vente.dim_commercial(commercial_key),
    geography_key         bigint REFERENCES dwh_vente.dim_geography(geography_key),
    payment_term_key      bigint REFERENCES dwh_vente.dim_payment_term(payment_term_key),
    price_list_key        bigint REFERENCES dwh_vente.dim_price_list(price_list_key),
    document_type_key     bigint REFERENCES dwh_vente.dim_document_type(document_type_key),
    warehouse_key         bigint REFERENCES dwh_vente.dim_warehouse(warehouse_key),
    total_lines_amount    numeric(18, 4),
    grand_total_amount    numeric(18, 4),
    freight_amount        numeric(18, 4),
    charge_amount         numeric(18, 4),
    order_count           integer DEFAULT 1
);

CREATE TABLE dwh_vente.fact_sales_order_line (
    sales_order_line_key  bigserial PRIMARY KEY,
    c_orderline_id        bigint NOT NULL UNIQUE,
    c_order_id            bigint NOT NULL,
    document_no           varchar(80),
    doc_status            varchar(10),
    line_no               integer,
    order_date_key        integer REFERENCES dwh_vente.dim_date(date_key),
    customer_key          bigint REFERENCES dwh_vente.dim_customer(customer_key),
    commercial_key        bigint REFERENCES dwh_vente.dim_commercial(commercial_key),
    product_key           bigint REFERENCES dwh_vente.dim_product(product_key),
    product_category_key  bigint REFERENCES dwh_vente.dim_product_category(product_category_key),
    supplier_key          bigint REFERENCES dwh_vente.dim_supplier(supplier_key),
    warehouse_key         bigint REFERENCES dwh_vente.dim_warehouse(warehouse_key),
    document_type_key     bigint REFERENCES dwh_vente.dim_document_type(document_type_key),
    quantity_ordered      numeric(18, 4),
    quantity_delivered    numeric(18, 4),
    quantity_invoiced     numeric(18, 4),
    price_list            numeric(18, 4),
    price_actual          numeric(18, 4),
    discount_percent      numeric(18, 4),
    line_net_amount       numeric(18, 4),
    line_count            integer DEFAULT 1
);

CREATE TABLE dwh_vente.fact_invoice (
    invoice_key           bigserial PRIMARY KEY,
    c_invoice_id          bigint NOT NULL UNIQUE,
    c_order_id            bigint,
    document_no           varchar(80),
    doc_status            varchar(10),
    is_paid               boolean,
    invoice_date_key      integer REFERENCES dwh_vente.dim_date(date_key),
    customer_key          bigint REFERENCES dwh_vente.dim_customer(customer_key),
    commercial_key        bigint REFERENCES dwh_vente.dim_commercial(commercial_key),
    geography_key         bigint REFERENCES dwh_vente.dim_geography(geography_key),
    payment_term_key      bigint REFERENCES dwh_vente.dim_payment_term(payment_term_key),
    price_list_key        bigint REFERENCES dwh_vente.dim_price_list(price_list_key),
    document_type_key     bigint REFERENCES dwh_vente.dim_document_type(document_type_key),
    total_lines_amount    numeric(18, 4),
    grand_total_amount    numeric(18, 4),
    invoice_count         integer DEFAULT 1,
    paid_invoice_count    integer DEFAULT 0,
    unpaid_invoice_count  integer DEFAULT 0
);

CREATE TABLE dwh_vente.fact_invoice_line (
    invoice_line_key      bigserial PRIMARY KEY,
    c_invoiceline_id      bigint NOT NULL UNIQUE,
    c_invoice_id          bigint NOT NULL,
    c_orderline_id        bigint,
    document_no           varchar(80),
    line_no               integer,
    invoice_date_key      integer REFERENCES dwh_vente.dim_date(date_key),
    customer_key          bigint REFERENCES dwh_vente.dim_customer(customer_key),
    commercial_key        bigint REFERENCES dwh_vente.dim_commercial(commercial_key),
    product_key           bigint REFERENCES dwh_vente.dim_product(product_key),
    product_category_key  bigint REFERENCES dwh_vente.dim_product_category(product_category_key),
    supplier_key          bigint REFERENCES dwh_vente.dim_supplier(supplier_key),
    document_type_key     bigint REFERENCES dwh_vente.dim_document_type(document_type_key),
    quantity_invoiced     numeric(18, 4),
    price_list            numeric(18, 4),
    price_actual          numeric(18, 4),
    line_net_amount       numeric(18, 4),
    invoice_line_count    integer DEFAULT 1
);

CREATE TABLE dwh_vente.fact_delivery (
    delivery_key          bigserial PRIMARY KEY,
    m_inout_id            bigint NOT NULL UNIQUE,
    c_order_id            bigint,
    document_no           varchar(80),
    doc_status            varchar(10),
    movement_date_key     integer REFERENCES dwh_vente.dim_date(date_key),
    customer_key          bigint REFERENCES dwh_vente.dim_customer(customer_key),
    geography_key         bigint REFERENCES dwh_vente.dim_geography(geography_key),
    warehouse_key         bigint REFERENCES dwh_vente.dim_warehouse(warehouse_key),
    document_type_key     bigint REFERENCES dwh_vente.dim_document_type(document_type_key),
    delivery_count        integer DEFAULT 1
);

CREATE TABLE dwh_vente.fact_delivery_line (
    delivery_line_key          bigserial PRIMARY KEY,
    m_inoutline_id             bigint NOT NULL UNIQUE,
    m_inout_id                 bigint NOT NULL,
    c_orderline_id             bigint,
    line_no                    integer,
    movement_date_key          integer REFERENCES dwh_vente.dim_date(date_key),
    customer_key               bigint REFERENCES dwh_vente.dim_customer(customer_key),
    product_key                bigint REFERENCES dwh_vente.dim_product(product_key),
    product_category_key       bigint REFERENCES dwh_vente.dim_product_category(product_category_key),
    supplier_key               bigint REFERENCES dwh_vente.dim_supplier(supplier_key),
    warehouse_key              bigint REFERENCES dwh_vente.dim_warehouse(warehouse_key),
    movement_quantity          numeric(18, 4),
    entered_quantity           numeric(18, 4),
    quantity_on_hand_snapshot  numeric(18, 4),
    quantity_reserved_snapshot numeric(18, 4),
    availability_indicator     varchar(255),
    delivery_line_count        integer DEFAULT 1
);

CREATE TABLE dwh_vente.fact_payment_allocation (
    payment_allocation_key     bigserial PRIMARY KEY,
    c_allocationline_id        bigint NOT NULL UNIQUE,
    c_allocationhdr_id         bigint,
    c_invoice_id               bigint,
    c_payment_id               bigint,
    c_order_id                 bigint,
    payment_document_no        varchar(80),
    allocation_date_key        integer REFERENCES dwh_vente.dim_date(date_key),
    customer_key               bigint REFERENCES dwh_vente.dim_customer(customer_key),
    commercial_key             bigint REFERENCES dwh_vente.dim_commercial(commercial_key),
    payment_document_type_key  bigint REFERENCES dwh_vente.dim_document_type(document_type_key),
    allocated_amount           numeric(18, 4),
    discount_amount            numeric(18, 4),
    writeoff_amount            numeric(18, 4),
    overunder_amount           numeric(18, 4),
    payment_amount             numeric(18, 4),
    allocation_count           integer DEFAULT 1
);

CREATE TABLE dwh_vente.fact_stock_snapshot (
    stock_snapshot_key         bigserial PRIMARY KEY,
    snapshot_date_key          integer REFERENCES dwh_vente.dim_date(date_key),
    m_product_id               bigint,
    m_attribute_set_instance_id bigint,
    product_key                bigint REFERENCES dwh_vente.dim_product(product_key),
    product_category_key       bigint REFERENCES dwh_vente.dim_product_category(product_category_key),
    supplier_key               bigint REFERENCES dwh_vente.dim_supplier(supplier_key),
    warehouse_key              bigint REFERENCES dwh_vente.dim_warehouse(warehouse_key),
    quantity_on_hand           numeric(18, 4),
    quantity_reserved          numeric(18, 4),
    quantity_available         numeric(18, 4),
    quantity_ordered           numeric(18, 4),
    stock_row_count            integer DEFAULT 1
);

-- ============================================================
-- Draft mart names for later Step 3/Step 4 implementation
-- ============================================================

-- mart_sales_overview
-- mart_sales_by_commercial
-- mart_sales_by_customer
-- mart_sales_by_product
-- mart_sales_by_supplier
-- mart_sales_by_region
-- mart_order_to_invoice_flow
-- mart_payment_status
-- mart_stock_risk
