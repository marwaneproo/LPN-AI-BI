-- =============================================================================
-- LPN AI-BI  ·  Staging Schema DDL (DRAFT)
-- Task     : BI-06 – Staging Schema Design
-- Date     : 2026-06-25
-- Branch   : bi-polished-dashboard
-- Author   : Claude Sonnet 4.6 (design only)
-- Status   : DRAFT – DO NOT EXECUTE IN PRODUCTION
--
-- Purpose  : Defines the staging schema and all stg_* tables.
--            Columns mirror the source CSV/xlsx files exactly.
--            No business transformations are applied here.
--            Load-metadata columns (_loaded_at, _source_file,
--            _source_tag, _etl_run_id) are appended to every table.
--
-- Schema target : lpn_ai_bi  (PostgreSQL 18)
-- Safety notes  : No FK constraints in staging; no NOT NULL except PK.
--                 business.* schema is never modified by this script.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS staging;

COMMENT ON SCHEMA staging IS
  'Staging layer: raw loads from CSV/xlsx exports. No transformations.';

-- ---------------------------------------------------------------------------
-- Shared metadata columns (appended to every table)
-- ---------------------------------------------------------------------------
-- _loaded_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
-- _source_file TEXT        NOT NULL   -- e.g. 'C_ORDER.csv'
-- _source_tag  TEXT        NOT NULL   -- 'csv_2025' | '2024_xlsx' | 'mixed'
-- _etl_run_id  UUID        NOT NULL   -- FK to etl.etl_run_log.etl_run_id


-- =============================================================================
-- 1. TRANSACTIONAL FACT SOURCES
-- =============================================================================

-- ---------------------------------------------------------------------------
-- stg_c_order
-- Source  : vente_2025_2026_import/csv/C_ORDER.csv  (2025-2026)
--         + Youssef_Extractions/data/Exported_LPN/2024_01_C_ORDER.xlsx (2024)
-- Rows    : 20,336 (CSV) + 18,024 (xlsx) = 38,360 before dedup
-- PK      : c_order_id  (confirmed unique per source in BI-02)
-- Wide    : 130 columns; all preserved raw
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_order (
    -- Primary key
    c_order_id                  BIGINT,

    -- System / audit columns (Compiere standard)
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,

    -- Document identity
    issotrx                     CHAR(1),
    documentno                  TEXT,
    docstatus                   TEXT,
    docaction                   TEXT,
    processing                  CHAR(1),
    processed                   CHAR(1),
    c_doctype_id                BIGINT,
    c_doctypetarget_id          BIGINT,
    description                 TEXT,

    -- Approval / transfer flags
    isapproved                  CHAR(1),
    iscreditapproved            CHAR(1),
    isdelivered                 CHAR(1),
    isinvoiced                  CHAR(1),
    isprinted                   CHAR(1),
    istransferred               CHAR(1),
    isselected                  CHAR(1),

    -- Business partners
    salesrep_id                 BIGINT,
    c_bpartner_id               BIGINT,
    c_bpartner_location_id      BIGINT,
    bill_bpartner_id            BIGINT,
    bill_location_id            BIGINT,
    bill_user_id                BIGINT,
    pay_bpartner_id             BIGINT,
    pay_location_id             BIGINT,
    ad_user_id                  BIGINT,

    -- Dates
    dateordered                 TIMESTAMPTZ,
    datepromised                TIMESTAMPTZ,
    dateprinted                 TIMESTAMPTZ,
    dateacct                    TIMESTAMPTZ,

    -- Reference
    poreference                 TEXT,

    -- Currency / payment
    c_currency_id               BIGINT,
    paymentrule                 TEXT,
    c_paymentterm_id            BIGINT,

    -- Fulfilment rules
    invoicerule                 TEXT,
    deliveryrule                TEXT,
    freightcostrule             TEXT,
    deliveryviarule             TEXT,
    m_shipper_id                BIGINT,
    c_charge_id                 BIGINT,
    chargeamt                   NUMERIC(18,4),
    priorityrule                TEXT,
    freightamt                  NUMERIC(18,4),

    -- Amounts
    totallines                  NUMERIC(18,4),
    grandtotal                  NUMERIC(18,4),

    -- Warehouse / price list
    m_warehouse_id              BIGINT,
    m_pricelist_id              BIGINT,
    istaxincluded               CHAR(1),
    isdiscountprinted           CHAR(1),

    -- Budget / project
    c_campaign_id               BIGINT,
    c_project_id                BIGINT,
    c_activity_id               BIGINT,
    ad_orgtrx_id                BIGINT,
    user1_id                    BIGINT,
    user2_id                    BIGINT,
    c_conversiontype_id         BIGINT,

    -- Payment linkage
    posted                      CHAR(1),
    c_payment_id                BIGINT,
    c_cashline_id               BIGINT,
    sendemail                   CHAR(1),

    -- Order type / operation (LPN custom)
    ref_order_id                BIGINT,
    isdropship                  CHAR(1),
    type_order                  TEXT,
    isnoted                     CHAR(1),
    mode_transmission           TEXT,
    typebc                      TEXT,
    typeoperation               TEXT,

    -- LPN logistics custom columns
    delaisembarq                BIGINT,
    dateembrqprevue             TIMESTAMPTZ,
    incoterm                    TEXT,
    imprimer                    TEXT,
    commentaire                 TEXT,
    issold                      CHAR(1),
    date_fin_validation         TIMESTAMPTZ,
    type_facturation            TEXT,
    categorie_livraison         TEXT,
    c_avoir_id                  BIGINT,
    mnt_total_disponible        NUMERIC(18,4),
    mnt_general_disponible      NUMERIC(18,4),
    isdevis                     CHAR(1),
    duree_validite_devis        BIGINT,
    suivi_devis                 TEXT,
    type_bcc                    TEXT,
    generated_order_id          BIGINT,
    generate_order              TEXT,
    isgrandesurface             CHAR(1),
    raf                         TEXT,           -- DW-03 DQ fix: source holds Y/N flag ('N'), not numeric
    print_titles                TEXT,
    isprepared                  CHAR(1),
    date_retour_depot           TIMESTAMPTZ,
    date_prolongement_depot     TIMESTAMPTZ,
    order_depot_master_id       BIGINT,
    control_batch               TEXT,
    sema                        TEXT,
    imprimession_cmd            TEXT,
    impression_cmd_achat        TEXT,
    date_completed              TIMESTAMPTZ,
    date_closed                 TIMESTAMPTZ,
    date_inprogress             TIMESTAMPTZ,
    date_limite_retour_office   TIMESTAMPTZ,
    date_rappel_office          TIMESTAMPTZ,
    total_prix_achat_net_neg    NUMERIC(18,4),
    datelrln                    TIMESTAMPTZ,
    delaisrlpn                  BIGINT,
    delaisprep                  BIGINT,
    scan_btn                    TEXT,
    gsbtn                       TEXT,
    iscmdcltcodifie             CHAR(1),
    mode_reception_cmd_clt      TEXT,
    issourceoffre               CHAR(1),
    specimen                    CHAR(1),
    demandeur_id                BIGINT,
    beneficiaire                TEXT,
    load_btn                    TEXT,
    source_order_id             BIGINT,
    m_inout_id                  BIGINT,
    chargeclient                TEXT,
    grandtotal_t                NUMERIC(18,4),
    totalines_t                 NUMERIC(18,4),
    mode_transport              TEXT,
    ismag                       CHAR(1),
    isarrond                    CHAR(1),
    browser_btn                 TEXT,
    export_excel_btn            TEXT,
    cmdnongroupee               CHAR(1),
    ville                       TEXT,
    btn_send_accuse             TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_order_pkey PRIMARY KEY (c_order_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_order IS
  'Sales order headers – mirrors C_ORDER.csv (2025-2026) and 2024_01_C_ORDER.xlsx. 130 cols.';


-- ---------------------------------------------------------------------------
-- stg_c_orderline
-- Source  : C_ORDERLINE.csv (354,910 rows) + 2024_02_C_ORDERLINE.xlsx (253 MB)
-- PK      : c_orderline_id  (confirmed unique per source in BI-02)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_orderline (
    c_orderline_id              BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,

    -- Header link
    c_order_id                  BIGINT,
    line                        INTEGER,

    -- Business partner
    c_bpartner_id               BIGINT,
    c_bpartner_location_id      BIGINT,

    -- Dates
    dateordered                 TIMESTAMPTZ,
    datepromised                TIMESTAMPTZ,
    datedelivered               TIMESTAMPTZ,
    dateinvoiced                TIMESTAMPTZ,
    description                 TEXT,

    -- Product / unit
    m_product_id                BIGINT,
    m_warehouse_id              BIGINT,
    c_uom_id                    BIGINT,

    -- Quantities
    qtyordered                  NUMERIC(18,4),
    qtyreserved                 NUMERIC(18,4),
    qtydelivered                NUMERIC(18,4),
    qtyinvoiced                 NUMERIC(18,4),

    -- Logistics
    m_shipper_id                BIGINT,
    c_currency_id               BIGINT,

    -- Pricing
    pricelist                   NUMERIC(18,4),
    priceactual                 NUMERIC(18,4),
    pricelimit                  NUMERIC(18,4),
    linenetamt                  NUMERIC(18,4),
    discount                    NUMERIC(18,4),
    freightamt                  NUMERIC(18,4),

    -- Charge / tax
    c_charge_id                 BIGINT,
    chargeamt                   NUMERIC(18,4),
    c_tax_id                    BIGINT,

    -- References
    s_resourceassignment_id     BIGINT,
    ref_orderline_id            BIGINT,
    m_attributesetinstance_id   BIGINT,
    isdescription               CHAR(1),
    processed                   CHAR(1),
    qtyentered                  NUMERIC(18,4),
    priceentered                NUMERIC(18,4),

    -- LPN custom
    auteur_id                   TEXT,
    editor_id                   TEXT,
    issold                      CHAR(1),
    typenonfourni               TEXT,
    isarticlenonfournis         CHAR(1),
    code_article                TEXT,
    date_parution               TIMESTAMPTZ,
    date_livraison_prevue       TIMESTAMPTZ,
    batchs                      TEXT,
    distributeur_id             BIGINT,
    qtyavailable                NUMERIC(18,4),
    mnt_net_disponible          NUMERIC(18,4),
    standard_discount           NUMERIC(18,4),
    pre_reservation             TEXT,
    substitutes_id              BIGINT,
    ppm_ttc                     NUMERIC(18,4),
    prix_net_ttc                NUMERIC(18,4),
    date_arrivage_prevue        TIMESTAMPTZ,
    printed_titles              TEXT,
    qtyproposee_cgs             NUMERIC(18,4),
    isproposed                  CHAR(1),
    date_proposition            TIMESTAMPTZ,
    m_requisitionline_id        BIGINT,
    vendorproductno             TEXT,
    remiseclt                   NUMERIC(18,4),
    isprepared                  CHAR(1),
    qteeffclt                   NUMERIC(18,4),
    prix_achat_neg_unit         NUMERIC(18,4),
    prix_achat_net_neg          NUMERIC(18,4),
    remiseflat                  NUMERIC(18,4),
    ppm                         NUMERIC(18,4),
    noitem                      TEXT,
    qtyreturned                 NUMERIC(18,4),
    datereturned                TIMESTAMPTZ,
    remiseforced                NUMERIC(18,4),
    ppmforced                   NUMERIC(18,4),
    tqtyres                     NUMERIC(18,4),
    semaph                      TEXT,
    upc                         TEXT,
    old_product_id              BIGINT,
    qtyreserved_bckup           NUMERIC(18,4),
    mnttva                      NUMERIC(18,4),
    mntttc                      NUMERIC(18,4),
    qtycmdfrs                   NUMERIC(18,4),
    isproductautochanged        CHAR(1),
    date_dispo                  TIMESTAMPTZ,
    discount_t                  NUMERIC(18,4),
    priceactual_t               NUMERIC(18,4),
    linenetamt_t                NUMERIC(18,4),
    mnttva_t                    NUMERIC(18,4),
    mntttc_t                    NUMERIC(18,4),
    qtydelivered_temp           NUMERIC(18,4),
    temp                        TEXT,
    qtynonfourni                NUMERIC(18,4),
    source_sold                 TEXT,
    qtyavoir                    NUMERIC(18,4),
    priceenteredtmp             NUMERIC(18,4),
    description_product         TEXT,
    pricearrond                 NUMERIC(18,4),
    chp1                        TEXT,
    chp2                        TEXT,
    chp3                        TEXT,
    chp4                        TEXT,
    chp5                        TEXT,
    chp6                        TEXT,
    chp7                        TEXT,
    chp8                        TEXT,
    chp9                        TEXT,
    chp10                       TEXT,
    solde                       TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_orderline_pkey PRIMARY KEY (c_orderline_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_orderline IS
  'Sales order lines – mirrors C_ORDERLINE.csv + 2024_02. ~100 cols. Chunked ETL required (253 MB xlsx).';


-- ---------------------------------------------------------------------------
-- stg_c_invoice
-- Source  : C_INVOICE.csv (6,658 rows) + 2024_03_C_INVOICE.xlsx (5,264 rows)
-- PK      : c_invoice_id  (confirmed unique per source in BI-02)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_invoice (
    c_invoice_id                BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,

    issotrx                     CHAR(1),
    documentno                  TEXT,
    docstatus                   TEXT,
    docaction                   TEXT,
    processing                  CHAR(1),
    processed                   CHAR(1),
    posted                      CHAR(1),
    c_doctype_id                BIGINT,
    c_doctypetarget_id          BIGINT,
    c_order_id                  BIGINT,        -- 29.9% null – valid (BI-02)
    description                 TEXT,

    isapproved                  CHAR(1),
    istransferred               CHAR(1),
    isprinted                   CHAR(1),
    ispaid                      CHAR(1),

    salesrep_id                 BIGINT,
    c_bpartner_id               BIGINT,
    c_bpartner_location_id      BIGINT,
    ad_user_id                  BIGINT,
    commercial_id               BIGINT,

    dateinvoiced                TIMESTAMPTZ,
    dateprinted                 TIMESTAMPTZ,
    dateacct                    TIMESTAMPTZ,
    dateordered                 TIMESTAMPTZ,

    poreference                 TEXT,
    isdiscountprinted           CHAR(1),
    c_currency_id               BIGINT,
    paymentrule                 TEXT,
    c_paymentterm_id            BIGINT,

    c_charge_id                 BIGINT,
    chargeamt                   NUMERIC(18,4),
    totallines                  NUMERIC(18,4),
    grandtotal                  NUMERIC(18,4),

    m_pricelist_id              BIGINT,
    istaxincluded               CHAR(1),
    c_campaign_id               BIGINT,
    c_project_id                BIGINT,
    c_activity_id               BIGINT,
    c_payment_id                BIGINT,
    c_cashline_id               BIGINT,
    createfrom                  TEXT,
    generateto                  TEXT,
    sendemail                   CHAR(1),
    copyfrom                    TEXT,
    isselfservice               CHAR(1),
    ad_orgtrx_id                BIGINT,
    user1_id                    BIGINT,
    user2_id                    BIGINT,
    c_conversiontype_id         BIGINT,
    ispayschedulevalid          CHAR(1),
    ref_invoice_id              BIGINT,
    isindispute                 CHAR(1),

    -- LPN custom
    c_avis_embarquement_id      BIGINT,
    poids                       NUMERIC(18,4),
    c_type_avoir_id             BIGINT,
    mode_paiment                TEXT,
    print_invoice               TEXT,
    isinvtof                    CHAR(1),
    echeance                    TIMESTAMPTZ,
    c_engagement_id             BIGINT,
    fret                        NUMERIC(18,4),
    fob                         TEXT,
    tempsalerep                 TEXT,
    invsource_avoir_id          BIGINT,
    specimen                    TEXT,
    remise_global               NUMERIC(18,4),
    m_inout_id                  BIGINT,
    oldno                       TEXT,
    fob_t                       TEXT,
    temp                        TEXT,
    totalines_t                 NUMERIC(18,4),
    sema                        TEXT,
    grandtotal_t                NUMERIC(18,4),
    compte_charge               TEXT,
    impute                      TEXT,
    print_invoice_laser         TEXT,
    btn_import_xls              TEXT,
    btn_delete_xls              TEXT,
    btn_export_xls              TEXT,
    c_dossier_import_export_id  BIGINT,
    colisage                    TEXT,
    incoterme                   TEXT,
    num_compte                  TEXT,
    btn_print_avoir             TEXT,
    montantfactureinitial       NUMERIC(18,4),
    btn_print_inv_gs            TEXT,
    btn_print_av_gs_1           TEXT,
    btn_print_av_gs_2           TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_invoice_pkey PRIMARY KEY (c_invoice_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_invoice IS
  'Customer invoices – mirrors C_INVOICE.csv + 2024_03. c_order_id 29.9% null (valid per BI-02).';


-- ---------------------------------------------------------------------------
-- stg_c_invoiceline
-- Source  : C_INVOICELINE.csv (335,877 rows) + 2024_04_C_INVOICELINE.xlsx (80 MB)
-- PK      : c_invoiceline_id  (confirmed unique per source in BI-02)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_invoiceline (
    c_invoiceline_id            BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,

    c_invoice_id                BIGINT,
    c_orderline_id              BIGINT,        -- bridge to order lines
    m_inoutline_id              BIGINT,        -- bridge to delivery lines
    line                        INTEGER,
    description                 TEXT,

    m_product_id                BIGINT,
    qtyinvoiced                 NUMERIC(18,4),
    pricelist                   NUMERIC(18,4),
    priceactual                 NUMERIC(18,4),
    pricelimit                  NUMERIC(18,4),
    linenetamt                  NUMERIC(18,4),
    c_charge_id                 BIGINT,
    chargeamt                   NUMERIC(18,4),
    c_uom_id                    BIGINT,
    c_tax_id                    BIGINT,
    s_resourceassignment_id     BIGINT,
    a_asset_id                  BIGINT,
    taxamt                      NUMERIC(18,4),
    m_attributesetinstance_id   BIGINT,
    isdescription               CHAR(1),
    isprinted                   CHAR(1),
    linetotalamt                NUMERIC(18,4),

    ref_invoiceline_id          BIGINT,
    processed                   CHAR(1),
    qtyentered                  NUMERIC(18,4),
    priceentered                NUMERIC(18,4),
    isreclamed                  CHAR(1),
    vendorproductno             TEXT,
    ppm                         NUMERIC(18,4),
    remiseflat                  NUMERIC(18,4),
    discount                    NUMERIC(18,4),
    remisematrice               NUMERIC(18,4),
    ppmforced                   NUMERIC(18,4),
    remiseforced                NUMERIC(18,4),
    batchs                      TEXT,
    remise_frs_matrice          NUMERIC(18,4),
    remise_a_saisir             NUMERIC(18,4),
    linetotalamtdh              NUMERIC(18,4),
    exprate                     NUMERIC(18,4),
    isreversable                CHAR(1),
    out_avoirclient             TEXT,           -- DW-03 DQ fix: source holds free-text avoir notes, not an id
    priceactualdh               NUMERIC(18,4),
    qtyreceived                 NUMERIC(18,4),
    linetotalamt_bckup          NUMERIC(18,4),
    linenetamt_t                NUMERIC(18,4),
    priceenteredtmp             NUMERIC(18,4),
    priceactual_t               NUMERIC(18,4),
    temp                        TEXT,
    ecart                       NUMERIC(18,4),
    valeur_ecart_dev            NUMERIC(18,4),
    valeur_ecart_dh             NUMERIC(18,4),
    rapproche                   CHAR(1),
    list_afn                    TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_invoiceline_pkey PRIMARY KEY (c_invoiceline_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_invoiceline IS
  'Invoice lines – mirrors C_INVOICELINE.csv + 2024_04. Bridges to orderlines and deliveries.';


-- =============================================================================
-- 2. DELIVERY SOURCES
-- =============================================================================

-- ---------------------------------------------------------------------------
-- stg_m_inout  (delivery headers)
-- Source  : M_INOUT.csv (18,718 rows) + 2024_05_M_INOUT.xlsx (44,232 rows)
-- PK      : m_inout_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_m_inout (
    m_inout_id                  BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,

    issotrx                     CHAR(1),
    documentno                  TEXT,
    docaction                   TEXT,
    docstatus                   TEXT,
    posted                      CHAR(1),
    processing                  CHAR(1),
    processed                   CHAR(1),
    c_doctype_id                BIGINT,
    description                 TEXT,

    c_order_id                  BIGINT,
    dateordered                 TIMESTAMPTZ,
    isprinted                   CHAR(1),
    movementtype                TEXT,
    movementdate                TIMESTAMPTZ,
    dateacct                    TIMESTAMPTZ,

    c_bpartner_id               BIGINT,
    c_bpartner_location_id      BIGINT,
    m_warehouse_id              BIGINT,
    poreference                 TEXT,

    deliveryrule                TEXT,
    freightcostrule             TEXT,
    freightamt                  NUMERIC(18,4),
    deliveryviarule             TEXT,
    m_shipper_id                BIGINT,
    c_charge_id                 BIGINT,
    chargeamt                   NUMERIC(18,4),
    priorityrule                TEXT,
    dateprinted                 TIMESTAMPTZ,
    c_invoice_id                BIGINT,
    createfrom                  TEXT,
    generateto                  TEXT,
    sendemail                   CHAR(1),
    ad_user_id                  BIGINT,
    salesrep_id                 BIGINT,
    nopackages                  INTEGER,
    pickdate                    TIMESTAMPTZ,
    shipdate                    TIMESTAMPTZ,
    trackingno                  TEXT,
    ad_orgtrx_id                BIGINT,
    c_project_id                BIGINT,
    c_campaign_id               BIGINT,
    c_activity_id               BIGINT,
    user1_id                    BIGINT,
    user2_id                    BIGINT,
    datereceived                TIMESTAMPTZ,
    isintransit                 CHAR(1),
    ref_inout_id                BIGINT,
    createconfirm               TEXT,
    createpackage               TEXT,
    isapproved                  CHAR(1),
    isindispute                 CHAR(1),

    -- LPN custom
    date_emission               TIMESTAMPTZ,
    montant_livraison           NUMERIC(18,4),
    c_currency_id               BIGINT,
    nombre_palette              INTEGER,
    nbr_colis_palette           INTEGER,
    c_qualif_type_date_id       BIGINT,
    impexp_id                   BIGINT,
    no_autorisation             TEXT,
    issent                      CHAR(1),
    internaluser_id             BIGINT,
    iscolis                     CHAR(1),
    date_completed              TIMESTAMPTZ,
    date_closed                 TIMESTAMPTZ,
    date_inprogress             TIMESTAMPTZ,
    isdepot                     CHAR(1),
    noavisemb                   TEXT,
    sg                          TEXT,
    isoffice                    CHAR(1),
    m_preparation_id            BIGINT,
    multiordersstrg             TEXT,
    datedesactivation           TIMESTAMPTZ,
    villpays                    TEXT,
    related_invoice_id          BIGINT,
    code_client                 TEXT,
    payment_rule                TEXT,
    grandtotal                  NUMERIC(18,4),
    orderdoctype_id             BIGINT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_m_inout_pkey PRIMARY KEY (m_inout_id, _source_tag)
);

COMMENT ON TABLE staging.stg_m_inout IS
  'Delivery / shipment headers (M_INOUT). Filter ISSOTRX=Y for sales deliveries.';


-- ---------------------------------------------------------------------------
-- stg_m_inoutline  (delivery lines)
-- Source  : M_INOUTLINE.csv (356,410 rows) + 2024_06_M_INOUTLINE.xlsx (132 MB)
-- PK      : m_inoutline_id
-- Chunked ETL required for xlsx (132 MB)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_m_inoutline (
    m_inoutline_id              BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,

    line                        INTEGER,
    description                 TEXT,
    m_inout_id                  BIGINT,
    c_orderline_id              BIGINT,
    m_locator_id                BIGINT,
    m_product_id                BIGINT,
    c_uom_id                    BIGINT,
    movementqty                 NUMERIC(18,4),
    isinvoiced                  CHAR(1),
    m_attributesetinstance_id   BIGINT,
    isdescription               CHAR(1),
    confirmedqty                NUMERIC(18,4),
    pickedqty                   NUMERIC(18,4),
    scrappedqty                 NUMERIC(18,4),
    targetqty                   NUMERIC(18,4),
    ref_inoutline_id            BIGINT,
    processed                   CHAR(1),
    qtyentered                  NUMERIC(18,4),
    batchs                      TEXT,
    vendorproductno             TEXT,

    -- LPN custom
    motif_desact                TEXT,
    qtydouch                    NUMERIC(18,4),
    isscanned                   CHAR(1),
    m_rmaline_id                BIGINT,
    qtyonhand                   NUMERIC(18,4),
    qtyreserved                 NUMERIC(18,4),
    isreversable                CHAR(1),
    qtymaggap                   NUMERIC(18,4),
    valdh                       NUMERIC(18,4),
    pricelist                   NUMERIC(18,4),
    discontinued                CHAR(1),
    disponibilite               TEXT,
    mode_retour                 TEXT,
    motif_retour                TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_m_inoutline_pkey PRIMARY KEY (m_inoutline_id, _source_tag)
);

COMMENT ON TABLE staging.stg_m_inoutline IS
  'Delivery lines (M_INOUTLINE). Chunked ETL required for xlsx. Links orderlines to delivery.';


-- =============================================================================
-- 3. PAYMENT & ALLOCATION SOURCES
-- =============================================================================

-- ---------------------------------------------------------------------------
-- stg_c_payment
-- Source  : C_PAYMENT.csv (1,443 rows) + 2024_07_C_PAYMENT.xlsx (3,775 rows)
-- PK      : c_payment_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_payment (
    c_payment_id                BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,

    documentno                  TEXT,
    datetrx                     TIMESTAMPTZ,
    isreceipt                   CHAR(1),
    c_doctype_id                BIGINT,
    trxtype                     TEXT,
    c_bankaccount_id            BIGINT,
    c_bpartner_id               BIGINT,
    c_invoice_id                BIGINT,
    c_bp_bankaccount_id         BIGINT,
    c_paymentbatch_id           BIGINT,
    tendertype                  TEXT,
    c_currency_id               BIGINT,
    payamt                      NUMERIC(18,4),
    discountamt                 NUMERIC(18,4),
    writeoffamt                 NUMERIC(18,4),
    taxamt                      NUMERIC(18,4),
    isapproved                  CHAR(1),
    docstatus                   TEXT,
    docaction                   TEXT,
    isreconciled                CHAR(1),
    isallocated                 CHAR(1),
    isonline                    CHAR(1),
    processed                   CHAR(1),
    posted                      CHAR(1),
    isoverunderpayment          CHAR(1),
    overunderamt                NUMERIC(18,4),
    chargeamt                   NUMERIC(18,4),
    c_charge_id                 BIGINT,
    description                 TEXT,
    dateacct                    TIMESTAMPTZ,
    c_order_id                  BIGINT,
    c_avoir_id                  BIGINT,
    ismultiinvoice              CHAR(1),
    ad_user_id                  BIGINT,
    dateecheance                TIMESTAMPTZ,
    porteur                     TEXT,
    ad_orgtrx_id                BIGINT,
    c_campaign_id               BIGINT,
    c_activity_id               BIGINT,
    user1_id                    BIGINT,
    user2_id                    BIGINT,
    c_conversiontype_id         BIGINT,
    c_project_id                BIGINT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_payment_pkey PRIMARY KEY (c_payment_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_payment IS 'Payment headers (C_PAYMENT).';


-- ---------------------------------------------------------------------------
-- stg_c_allocationhdr
-- Source  : C_ALLOCATIONHDR.csv (1,473 rows) + 2024_08 xlsx (4,113 rows)
-- PK      : c_allocationhdr_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_allocationhdr (
    c_allocationhdr_id          BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    documentno                  TEXT,
    description                 TEXT,
    datetrx                     TIMESTAMPTZ,
    dateacct                    TIMESTAMPTZ,
    c_currency_id               BIGINT,
    approvalamt                 NUMERIC(18,4),
    ismanual                    CHAR(1),
    docstatus                   TEXT,
    docaction                   TEXT,
    isapproved                  CHAR(1),
    processing                  CHAR(1),
    processed                   CHAR(1),
    posted                      CHAR(1),

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_allocationhdr_pkey PRIMARY KEY (c_allocationhdr_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_allocationhdr IS 'Payment allocation headers (C_ALLOCATIONHDR).';


-- ---------------------------------------------------------------------------
-- stg_c_allocationline
-- Source  : C_ALLOCATIONLINE.csv (4,212 rows) + 2024_09 xlsx (86,863 rows)
-- PK      : c_allocationline_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_allocationline (
    c_allocationline_id         BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,

    allocationno                TEXT,
    c_currency_id               BIGINT,
    datetrx                     TIMESTAMPTZ,
    ismanual                    CHAR(1),

    -- Invoice and payment links
    c_invoice_id                BIGINT,
    c_bpartner_id               BIGINT,
    c_order_id                  BIGINT,
    c_payment_id                BIGINT,
    c_cashline_id               BIGINT,
    c_allocationhdr_id          BIGINT,

    -- Measures
    amount                      NUMERIC(18,4),
    discountamt                 NUMERIC(18,4),
    writeoffamt                 NUMERIC(18,4),
    overunderamt                NUMERIC(18,4),
    posted                      CHAR(1),

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_allocationline_pkey PRIMARY KEY (c_allocationline_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_allocationline IS
  'Payment allocation lines. PK used by fact_payment_allocation. Bridge: invoice←→payment.';


-- =============================================================================
-- 4. BUSINESS PARTNER SOURCES
-- =============================================================================

-- ---------------------------------------------------------------------------
-- stg_c_bpartner  (customers)
-- Source  : C_BPARTNER.csv (823 rows) + 2024_10_C_BPARTNER.xlsx (42,434 rows)
-- PK      : c_bpartner_id
-- Wide    : 120+ columns; all LPN-custom columns included
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_bpartner (
    c_bpartner_id               BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,

    value                       TEXT,
    name                        TEXT,
    name2                       TEXT,
    description                 TEXT,
    issummary                   CHAR(1),
    c_bp_group_id               BIGINT,
    isonetime                   CHAR(1),
    isprospect                  CHAR(1),
    isvendor                    CHAR(1),
    iscustomer                  CHAR(1),
    isemployee                  CHAR(1),
    issalesrep                  CHAR(1),
    referenceno                 TEXT,
    taxid                       TEXT,
    istaxexempt                 CHAR(1),
    rating                      TEXT,
    paymentrule                 TEXT,
    so_creditlimit              NUMERIC(18,4),
    so_creditused               NUMERIC(18,4),
    c_paymentterm_id            BIGINT,
    m_pricelist_id              BIGINT,
    m_discountschema_id         BIGINT,
    isdiscountprinted           CHAR(1),
    poreference                 TEXT,
    paymentrulepo               TEXT,           -- DW-03 DQ fix: source holds payment-rule code ('T'), not an id
    po_pricelist_id             BIGINT,
    po_discountschema_id        BIGINT,
    po_paymentterm_id           BIGINT,
    documentcopies              INTEGER,
    invoicerule                 TEXT,
    deliveryrule                TEXT,
    freightcostrule             TEXT,
    deliveryviarule             TEXT,
    salesrep_id                 BIGINT,
    sendemail                   CHAR(1),
    bpartner_parent_id          BIGINT,
    socreditstatus              TEXT,
    flatdiscount                NUMERIC(18,4),
    totalopenbalance            NUMERIC(18,4),
    firstsale                   TIMESTAMPTZ,

    -- LPN custom (client classification)
    code_client                 TEXT,
    forme_juridique             TEXT,
    date_creation               TIMESTAMPTZ,
    patente                     TEXT,
    rc                          TEXT,
    c_categorie_client_id       BIGINT,
    c_regularite_client_id      BIGINT,
    isediteur                   CHAR(1),
    genecode                    TEXT,
    c_client_type_id            BIGINT,
    c_client_soustype_id        BIGINT,
    mode_transmission           TEXT,
    delaisembarq                BIGINT,
    isenfond                    CHAR(1),
    mode_facturation            TEXT,
    c_tax_id                    BIGINT,
    ca_retour_rate              NUMERIC(18,4),
    quantity_retour_rate        NUMERIC(18,4),
    ispreresacceptee            CHAR(1),
    isbloque                    CHAR(1),
    incoterm                    TEXT,
    isexclusif                  CHAR(1),
    motif_blocage               TEXT,
    date_blocage                TIMESTAMPTZ,
    ad_user_id                  BIGINT,
    email                       TEXT,
    isgrandesurface             CHAR(1),
    sigle                       TEXT,
    m_shipper_id                BIGINT,
    gencodeed                   TEXT,
    ispurchasesrep              CHAR(1),
    delaishlrln                 BIGINT,
    delaisprep                  BIGINT,
    c_currency_id               BIGINT,
    isdilcomordered             CHAR(1),
    iscalogonline               CHAR(1),
    nbtitres                    INTEGER,
    magasin                     TEXT,
    client_magasin              TEXT,
    ismagazin                   CHAR(1),
    isclient_magazin            CHAR(1),
    c_magasin_id                BIGINT,
    code_pedagogique            TEXT,
    client_specimen             TEXT,
    code_academie               TEXT,
    libelle_academie            TEXT,
    code_delegation             TEXT,
    libelle_delegation          TEXT,
    c_client_typeacademie_id    BIGINT,
    c_client_soustypeacademie_id BIGINT,
    c_typeacademie_id           BIGINT,
    c_stypeacademie_id          BIGINT,
    compte_unique               TEXT,
    compte_charge               TEXT,
    ice                         TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_bpartner_pkey PRIMARY KEY (c_bpartner_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_bpartner IS
  'Business partners (customers + vendors merged). Filter ISCUSTOMER=Y for dim_customer.';


-- ---------------------------------------------------------------------------
-- stg_c_bpartner_vendor  (vendor subset – separate source files)
-- Source  : C_BPARTNER_VENDOR.csv (14,264 rows)
--         + 2024_19_C_BPARTNER_VENDORS.xlsx + 2024_21_C_BPARTNER_VENDORS.xlsx
-- PK      : c_bpartner_id
-- Same columns as stg_c_bpartner; kept separate for ETL source traceability
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_bpartner_vendor (
    c_bpartner_id               BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    value                       TEXT,
    name                        TEXT,
    name2                       TEXT,
    description                 TEXT,
    issummary                   CHAR(1),
    c_bp_group_id               BIGINT,
    isonetime                   CHAR(1),
    isprospect                  CHAR(1),
    isvendor                    CHAR(1),
    iscustomer                  CHAR(1),
    isemployee                  CHAR(1),
    issalesrep                  CHAR(1),
    taxid                       TEXT,
    paymentrule                 TEXT,
    so_creditlimit              NUMERIC(18,4),
    c_paymentterm_id            BIGINT,
    m_pricelist_id              BIGINT,
    salesrep_id                 BIGINT,
    sendemail                   CHAR(1),
    socreditstatus              TEXT,
    totalopenbalance            NUMERIC(18,4),
    email                       TEXT,
    isbloque                    CHAR(1),
    c_currency_id               BIGINT,
    ice                         TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_bpartner_vendor_pkey PRIMARY KEY (c_bpartner_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_bpartner_vendor IS
  'Vendor business partners. Source for dim_supplier. Separate from stg_c_bpartner for traceability.';


-- ---------------------------------------------------------------------------
-- stg_c_bpartner_location
-- Source  : C_BPARTNER_LOCATION.csv (888 rows) + 2024_11 xlsx (4,221 rows)
-- PK      : c_bpartner_location_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_bpartner_location (
    c_bpartner_location_id      BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    name                        TEXT,
    isbillto                    CHAR(1),
    isshipto                    CHAR(1),
    ispayfrom                   CHAR(1),
    isremitto                   CHAR(1),
    phone                       TEXT,
    phone2                      TEXT,
    fax                         TEXT,
    isdn                        TEXT,
    c_salesregion_id            BIGINT,
    c_bpartner_id               BIGINT,
    c_location_id               BIGINT,
    isadrenlevement             CHAR(1),
    email                       TEXT,
    gsm                         TEXT,
    villepays                   TEXT,
    sectordetail                TEXT,
    code_zone_pedagogique       TEXT,
    secteur_pedagogique         TEXT,
    ville_id                    BIGINT,
    codif_ville                 TEXT,
    code_delegue                TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_bpartner_location_pkey PRIMARY KEY (c_bpartner_location_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_bpartner_location IS
  'Business partner locations. Source for dim_geography (bill-to / ship-to).';


-- ---------------------------------------------------------------------------
-- stg_c_location  (physical addresses)
-- Source  : C_LOCATION.csv (772 rows) + 2024_12 xlsx (4,108 rows)
-- PK      : c_location_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_location (
    c_location_id               BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    address1                    TEXT,
    address2                    TEXT,
    city                        TEXT,
    postal                      TEXT,
    postal_add                  TEXT,
    c_country_id                BIGINT,
    c_region_id                 BIGINT,
    c_city_id                   BIGINT,
    regionname                  TEXT,
    address3                    TEXT,
    address4                    TEXT,
    cityname                    TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_location_pkey PRIMARY KEY (c_location_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_location IS 'Physical address records for dim_geography.';


-- =============================================================================
-- 5. PRODUCT & TAXONOMY SOURCES
-- =============================================================================

-- ---------------------------------------------------------------------------
-- stg_m_product
-- Source  : M_PRODUCT.csv (35,822 rows) — xlsx EXCLUDED (2,272 MB)
-- PK      : m_product_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_m_product (
    m_product_id                BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,

    value                       TEXT,
    name                        TEXT,
    description                 TEXT,
    documentnote                TEXT,
    help                        TEXT,
    upc                         TEXT,
    sku                         TEXT,
    c_uom_id                    BIGINT,
    salesrep_id                 BIGINT,
    issummary                   CHAR(1),
    isstocked                   CHAR(1),
    ispurchased                 CHAR(1),
    issold                      CHAR(1),
    isbom                       CHAR(1),
    isverified                  CHAR(1),
    c_revenuerecognition_id     BIGINT,
    m_product_category_id       BIGINT,
    classification              TEXT,
    volume                      NUMERIC(18,4),
    weight                      NUMERIC(18,4),
    shelfwidth                  NUMERIC(18,4),
    shelfheight                 NUMERIC(18,4),
    shelfdepth                  NUMERIC(18,4),
    unitsperpallet              NUMERIC(18,4),
    c_taxcategory_id            BIGINT,
    producttype                 TEXT,
    guaranteedays               INTEGER,
    versionno                   TEXT,
    m_attributeset_id           BIGINT,
    m_attributesetinstance_id   BIGINT,
    m_freightcategory_id        BIGINT,
    m_locator_id                BIGINT,
    guaranteedaysmin            INTEGER,
    discontinued                CHAR(1),
    discontinuedby              TEXT,         -- DW-04: source holds dates, not a user id (BI-06 mis-typed)

    -- LPN book/editorial custom columns
    code_isbn                   TEXT,
    presentation_editeur        TEXT,
    date_parution               TIMESTAMPTZ,
    code_frs                    TEXT,
    m_product_theme_id          BIGINT,
    titre                       TEXT,
    code_article                TEXT,
    new_product_id              BIGINT,
    old_product_id              BIGINT,
    m_product_famille_id        BIGINT,
    nom_frs                     TEXT,
    gestion                     TEXT,
    m_product_fond_id           BIGINT,
    date_livraison              TIMESTAMPTZ,
    ucm                         TEXT,
    isopx                       CHAR(1),
    isoffice                    CHAR(1),
    isncommande                 CHAR(1),
    motif_censure               TEXT,
    is_stop_distrib             CHAR(1),
    date_opx                    TIMESTAMPTZ,
    disponibilite               TEXT,
    m_product_type_id           BIGINT,
    m_product_collection_id     BIGINT,
    date_fin_commercialisation  TIMESTAMPTZ,
    code_retour                 TEXT,
    m_product_nmdouane_id       BIGINT,
    hd                          TEXT,
    opxm                        TEXT,
    isselected                  CHAR(1),
    observation                 TEXT,
    motif_opx                   TEXT,
    diffusion                   TEXT,
    collect_ser                 TEXT,
    libellecaisse               TEXT,
    editors                     TEXT,
    auteurs                     TEXT,
    groupfamille                TEXT,
    symbolisation               TEXT,
    editeurfixe                 TEXT,
    editeur                     TEXT,
    distributeur                TEXT,
    articlecampus               TEXT,
    m_product_campus_id         BIGINT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_m_product_pkey PRIMARY KEY (m_product_id, _source_tag)
);

COMMENT ON TABLE staging.stg_m_product IS
  'Products. CSV only (2,272 MB xlsx excluded per BI-05 TRAP rule).';


-- ---------------------------------------------------------------------------
-- stg_m_product_category
-- Source  : 2024_17_M_PRODUCT_CATEGORY.xlsx (41 rows)
-- PK      : m_product_category_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_m_product_category (
    m_product_category_id       BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    value                       TEXT,
    name                        TEXT,
    description                 TEXT,
    isdefault                   CHAR(1),
    plannedmargin               NUMERIC(18,4),
    a_asset_group_id            BIGINT,
    isselfservice               CHAR(1),
    ad_printcolor_id            BIGINT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_m_product_category_pkey PRIMARY KEY (m_product_category_id, _source_tag)
);

COMMENT ON TABLE staging.stg_m_product_category IS
  'Product categories (41 rows). Note: NAME has trailing \t; VALUE has trailing \n (BI-03 W-02/04).';


-- ---------------------------------------------------------------------------
-- stg_m_product_theme
-- Source  : M_PRODUCT_THEME.csv + 2024_21_M_PRODUCT_THEME.xlsx (1,513 rows)
-- PK      : m_product_theme_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_m_product_theme (
    m_product_theme_id          BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    value                       TEXT,
    name                        TEXT,
    description                 TEXT,
    m_product_fond_id           BIGINT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_m_product_theme_pkey PRIMARY KEY (m_product_theme_id, _source_tag)
);

COMMENT ON TABLE staging.stg_m_product_theme IS 'Product themes (thématiques) – 1,513 rows.';


-- ---------------------------------------------------------------------------
-- stg_m_product_type
-- Source  : 2024_20_M_PRODUCT_TYPE.xlsx (27 rows)
-- PK      : m_product_type_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_m_product_type (
    m_product_type_id           BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    value                       TEXT,
    name                        TEXT,
    description                 TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_m_product_type_pkey PRIMARY KEY (m_product_type_id, _source_tag)
);

COMMENT ON TABLE staging.stg_m_product_type IS 'Product types – 27 rows.';


-- ---------------------------------------------------------------------------
-- stg_m_product_collection
-- Source  : 2024_22_M_PRODUCT_COLLECTION.xlsx (~86K rows)
-- PK      : m_product_collection_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_m_product_collection (
    m_product_collection_id     BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    value                       TEXT,
    name                        TEXT,
    description                 TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_m_product_collection_pkey PRIMARY KEY (m_product_collection_id, _source_tag)
);

COMMENT ON TABLE staging.stg_m_product_collection IS 'Product collections/series – ~86K rows.';


-- ---------------------------------------------------------------------------
-- stg_m_product_po  (product-supplier pricing)
-- Source  : M_PRODUCT_PO.csv (39,359 rows) — xlsx EXCLUDED (995 MB)
-- PK      : (m_product_id, c_bpartner_id)  composite
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_m_product_po (
    m_product_id                BIGINT,
    c_bpartner_id               BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    iscurrentvendor             CHAR(1),
    c_uom_id                    BIGINT,
    c_currency_id               BIGINT,
    pricelist                   NUMERIC(18,4),
    pricepo                     NUMERIC(18,4),
    priceeffective              TIMESTAMPTZ,
    pricelastpo                 NUMERIC(18,4),
    pricelastinv                NUMERIC(18,4),
    vendorproductno             TEXT,
    upc                         TEXT,
    vendorcategory              TEXT,
    discontinued                CHAR(1),
    discontinuedby              BIGINT,
    order_min                   NUMERIC(18,4),
    order_pack                  NUMERIC(18,4),
    costperorder                NUMERIC(18,4),
    deliverytime_promised       INTEGER,
    deliverytime_actual         INTEGER,
    qualityrating               NUMERIC(18,4),
    royaltyamt                  NUMERIC(18,4),
    manufacturer                TEXT,
    date_fin_com                TIMESTAMPTZ,
    collection_serielle         TEXT,
    uaf                         TEXT,
    code_retour                 TEXT,
    disponibilite               TEXT,
    hd                          TEXT,
    majoxford                   TEXT,
    nbene                       TEXT,         -- DW-04: source holds free text (e.g. 'devise was null'), not numeric
    oldnoaf                     TEXT,
    motifmajprice               TEXT,
    motifmajprice_temp          TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_m_product_po_pkey PRIMARY KEY (m_product_id, c_bpartner_id, _source_tag)
);

COMMENT ON TABLE staging.stg_m_product_po IS
  'Product-supplier pricing. CSV only (995 MB xlsx excluded). Dedup: SEQNO ASC per product.';


-- =============================================================================
-- 6. GEOGRAPHY SOURCES
-- =============================================================================

-- ---------------------------------------------------------------------------
-- stg_c_region
-- Source  : C_REGION.csv (16 rows) + 2024_13 xlsx (87 rows)
-- PK      : c_region_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_region (
    c_region_id                 BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    name                        TEXT,
    description                 TEXT,
    c_country_id                BIGINT,
    isdefault                   CHAR(1),

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_region_pkey PRIMARY KEY (c_region_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_region IS
  'Geographic regions. NAME all-caps (BI-03 K-01) – INITCAP applied in dim build.';


-- ---------------------------------------------------------------------------
-- stg_c_city
-- Source  : C_CITY.csv (302 rows) + 2024_14 xlsx (364 rows)
-- PK      : c_city_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_city (
    c_city_id                   BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    name                        TEXT,
    locode                      TEXT,
    coordinates                 TEXT,
    postal                      TEXT,
    areacode                    TEXT,
    c_country_id                BIGINT,
    c_region_id                 BIGINT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_city_pkey PRIMARY KEY (c_city_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_city IS
  'Cities. NAME all-caps (BI-03 K-01). False-positive currency hits: AGADIR, MOHAMMEDIA (BI-03 C-03).';


-- ---------------------------------------------------------------------------
-- stg_c_salesregion
-- Source  : C_SALESREGION.csv (13 rows) + 2024_30 xlsx
-- PK      : c_salesregion_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_salesregion (
    c_salesregion_id            BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    value                       TEXT,
    name                        TEXT,
    description                 TEXT,
    issummary                   CHAR(1),
    salesrep_id                 BIGINT,
    isdefault                   CHAR(1),

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_salesregion_pkey PRIMARY KEY (c_salesregion_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_salesregion IS 'Sales regions – 13 rows.';


-- =============================================================================
-- 7. REFERENCE / LOOKUP SOURCES
-- =============================================================================

-- ---------------------------------------------------------------------------
-- stg_ad_user  (sales representatives)
-- Source  : AD_USER.csv (28 rows) + 2024_15 xlsx (31 rows)
-- PK      : ad_user_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_ad_user (
    ad_user_id                  BIGINT,
    name                        TEXT,
    description                 TEXT,
    isactive                    CHAR(1),
    c_bpartner_id               BIGINT,
    email                       TEXT,
    created                     TIMESTAMPTZ,
    updated                     TIMESTAMPTZ,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_ad_user_pkey PRIMARY KEY (ad_user_id, _source_tag)
);

COMMENT ON TABLE staging.stg_ad_user IS
  'Sales reps (AD_USER). NAME trailing space (BI-03 W-06) – TRIM+INITCAP in dim_commercial build.';


-- ---------------------------------------------------------------------------
-- stg_c_doctype
-- Source  : 2024_23_C_DOCTYPE.xlsx (79 rows)
-- PK      : c_doctype_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_doctype (
    c_doctype_id                BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    name                        TEXT,
    printname                   TEXT,
    description                 TEXT,
    docbasetype                 TEXT,
    issotrx                     CHAR(1),
    docsubtypeso                TEXT,
    hasproforma                 CHAR(1),
    c_doctypeproforma_id        BIGINT,
    c_doctypeshipment_id        BIGINT,
    c_doctypeinvoice_id         BIGINT,
    isdocnocontrolled           CHAR(1),
    docnosequence_id            BIGINT,
    gl_category_id              BIGINT,
    hascharges                  CHAR(1),
    documentnote                TEXT,
    isdefault                   CHAR(1),
    documentcopies              INTEGER,
    ad_printformat_id           BIGINT,
    isdefaultcounterdoc         CHAR(1),
    isshipconfirm               CHAR(1),
    ispickqaconfirm             CHAR(1),
    isintransit                 CHAR(1),
    issplitwhendifference       CHAR(1),
    c_doctypedifference_id      BIGINT,
    iscreatecounter             CHAR(1),

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_doctype_pkey PRIMARY KEY (c_doctype_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_doctype IS
  'Document types (79 rows). Filter ISSOTRX=Y for dim_document_type.';


-- ---------------------------------------------------------------------------
-- stg_c_paymentterm
-- Source  : 2024_28_C_PAYMENTTERM.xlsx (7 rows)
-- PK      : c_paymentterm_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_c_paymentterm (
    c_paymentterm_id            BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    name                        TEXT,
    description                 TEXT,
    documentnote                TEXT,
    afterdelivery               CHAR(1),
    isduefixed                  CHAR(1),
    netdays                     INTEGER,
    gracedays                   INTEGER,
    fixmonthcutoff              INTEGER,
    fixmonthday                 INTEGER,
    fixmonthoffset              INTEGER,
    discountdays                INTEGER,
    discount                    NUMERIC(18,4),
    discountdays2               INTEGER,
    discount2                   NUMERIC(18,4),
    isnextbusinessday           CHAR(1),
    isdefault                   CHAR(1),
    value                       TEXT,
    netday                      TEXT,
    isvalid                     CHAR(1),
    processing                  CHAR(1),

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_c_paymentterm_pkey PRIMARY KEY (c_paymentterm_id, _source_tag)
);

COMMENT ON TABLE staging.stg_c_paymentterm IS 'Payment terms reference (7 rows).';


-- ---------------------------------------------------------------------------
-- stg_m_pricelist
-- Source  : 2024_29_M_PRICELIST.xlsx (22 rows)
-- PK      : m_pricelist_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_m_pricelist (
    m_pricelist_id              BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    name                        TEXT,
    description                 TEXT,
    basepricelist_id            BIGINT,
    istaxincluded               CHAR(1),
    issopricelist               CHAR(1),
    isdefault                   CHAR(1),
    c_currency_id               BIGINT,
    enforcepricelimit           CHAR(1),
    commentaire                 TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_m_pricelist_pkey PRIMARY KEY (m_pricelist_id, _source_tag)
);

COMMENT ON TABLE staging.stg_m_pricelist IS 'Price lists (22 rows). Singleton C_CURRENCY_ID=239 (MAD).';


-- ---------------------------------------------------------------------------
-- stg_m_warehouse
-- Source  : 2024_26_M_WAREHOUSE.xlsx (7 rows)
-- PK      : m_warehouse_id
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_m_warehouse (
    m_warehouse_id              BIGINT,
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    isactive                    CHAR(1),
    created                     TIMESTAMPTZ,
    createdby                   BIGINT,
    updated                     TIMESTAMPTZ,
    updatedby                   BIGINT,
    value                       TEXT,
    name                        TEXT,
    description                 TEXT,
    c_location_id               BIGINT,
    separator                   TEXT,

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_m_warehouse_pkey PRIMARY KEY (m_warehouse_id, _source_tag)
);

COMMENT ON TABLE staging.stg_m_warehouse IS
  'Warehouses (7 rows). M_WAREHOUSE_ID=1,000,000 is singleton in transactional data.';


-- =============================================================================
-- 8. STOCK SNAPSHOT
-- =============================================================================

-- ---------------------------------------------------------------------------
-- stg_rv_storage
-- Source  : RV_STORAGE.csv (99,234 rows) + 18_RV_STORAGE xlsx
-- PK      : composite (m_product_id, m_attributesetinstance_id, m_warehouse_id, m_locator_id)
-- Semi-additive: do not SUM across snapshot dates
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging.stg_rv_storage (
    ad_client_id                BIGINT,
    ad_org_id                   BIGINT,
    m_product_id                BIGINT,
    value                       TEXT,
    name                        TEXT,
    description                 TEXT,
    upc                         TEXT,
    sku                         TEXT,
    c_uom_id                    BIGINT,
    m_product_category_id       BIGINT,
    classification              TEXT,
    weight                      NUMERIC(18,4),
    volume                      NUMERIC(18,4),
    versionno                   TEXT,
    guaranteedays               INTEGER,
    guaranteedaysmin            INTEGER,
    m_locator_id                BIGINT,
    m_warehouse_id              BIGINT,
    x                           TEXT,
    y                           TEXT,
    z                           TEXT,
    qtyonhand                   NUMERIC(18,4),
    qtyreserved                 NUMERIC(18,4),
    qtyavailable                NUMERIC(18,4),
    qtyordered                  NUMERIC(18,4),
    datelastinventory           TIMESTAMPTZ,
    m_attributesetinstance_id   BIGINT,
    m_attributeset_id           BIGINT,
    serno                       TEXT,
    lot                         TEXT,
    m_lot_id                    BIGINT,
    guaranteedate               TIMESTAMPTZ,
    shelflifedays               INTEGER,
    goodfordays                 INTEGER,
    shelfliferemainingpct       NUMERIC(18,4),

    -- Load metadata
    _loaded_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    _source_file                TEXT        NOT NULL,
    _source_tag                 TEXT        NOT NULL,
    _etl_run_id                 UUID        NOT NULL,

    CONSTRAINT stg_rv_storage_pkey
        PRIMARY KEY (m_product_id, m_attributesetinstance_id, m_warehouse_id, m_locator_id, _source_tag)
);

COMMENT ON TABLE staging.stg_rv_storage IS
  'Stock snapshot (semi-additive). Single snapshot per extraction date. Do not SUM across dates.';


-- =============================================================================
-- 9. ETL INFRASTRUCTURE TABLES
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS etl;

CREATE TABLE IF NOT EXISTS etl.etl_run_log (
    etl_run_id                  UUID         PRIMARY KEY,
    started_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at                TIMESTAMPTZ,
    status                      TEXT         NOT NULL DEFAULT 'RUNNING',
    triggered_by                TEXT,
    source_tag                  TEXT         NOT NULL,
    git_commit                  TEXT,
    notes                       TEXT
);

CREATE TABLE IF NOT EXISTS etl.etl_run_table_stats (
    stat_id                     BIGSERIAL    PRIMARY KEY,
    etl_run_id                  UUID         NOT NULL,
    table_name                  TEXT         NOT NULL,
    source_file                 TEXT,
    rows_read                   INTEGER,
    rows_inserted               INTEGER,
    rows_skipped_dup            INTEGER,
    rows_rejected               INTEGER,
    duration_ms                 INTEGER,
    completed_at                TIMESTAMPTZ  DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS etl.stg_rejects (
    reject_id                   BIGSERIAL    PRIMARY KEY,
    etl_run_id                  UUID         NOT NULL,
    source_table                TEXT         NOT NULL,
    source_file                 TEXT         NOT NULL,
    source_row                  INTEGER      NOT NULL,
    pk_value                    TEXT,
    reject_reason               TEXT         NOT NULL,
    reject_detail               TEXT,
    raw_row_json                JSONB,
    rejected_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stg_rejects_run ON etl.stg_rejects (etl_run_id);
CREATE INDEX IF NOT EXISTS idx_stg_rejects_table ON etl.stg_rejects (source_table, reject_reason);

-- =============================================================================
-- End of staging DDL draft
-- Run sqlglot parse validation before execution:
--   python -c "import sqlglot; sqlglot.parse(open('staging.sql').read(), dialect='postgres'); print('OK')"
-- =============================================================================
