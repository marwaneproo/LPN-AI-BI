from __future__ import annotations

import csv
import shutil
from datetime import datetime
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
SCHEMA_METADATA_PATH = REPO_ROOT / "docs" / "schema_metadata.csv"
BACKUP_DIR = REPO_ROOT / "local-snapshots" / "schema-metadata-backups"
EXPECTED_TABLES = {
    "C_ORDER",
    "C_ORDERLINE",
    "C_INVOICE",
    "C_INVOICELINE",
    "C_BPARTNER",
    "M_PRODUCT",
    "M_PRODUCT_CATEGORY",
    "C_DOCTYPE",
    "C_TAX",
    "AD_ORG",
    "C_ALLOCATIONLINE",
    "C_ALLOCATIONHDR",
    "C_PAYMENT",
    "M_INOUT",
    "M_INOUTLINE",
    "M_WAREHOUSE",
    "M_LOCATOR",
    "RV_STORAGE",
}

FIELDNAMES = [
    "table_name",
    "module",
    "description_en",
    "description_fr",
    "key_columns",
    "relations",
    "sensitive",
    "notes",
]


def main() -> None:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    backup_path = BACKUP_DIR / f"schema_metadata.backup-{datetime.now().strftime('%Y%m%d-%H%M%S')}.csv"
    shutil.copy2(SCHEMA_METADATA_PATH, backup_path)

    rows = _vente_rows()
    table_names = {row["table_name"] for row in rows}
    if table_names != EXPECTED_TABLES:
        missing = sorted(EXPECTED_TABLES - table_names)
        extra = sorted(table_names - EXPECTED_TABLES)
        raise SystemExit(f"Metadata table set mismatch. Missing={missing}; extra={extra}")

    with SCHEMA_METADATA_PATH.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDNAMES)
        writer.writeheader()
        writer.writerows(rows)

    _validate_written_csv()
    print(f"Updated {SCHEMA_METADATA_PATH}")
    print(f"Backup: {backup_path}")
    print(f"Rows: {len(rows)}")


def _vente_rows() -> list[dict[str, str]]:
    return [
        _row(
            "C_ORDER",
            "Sales",
            "Sales order header storing customer orders dates statuses document types warehouses and order totals.",
            "En-tete des commandes de vente avec client dates statuts types documents entrepot et totaux.",
            "C_ORDER_ID:bigint:Primary sales order identifier|DOCUMENTNO:varchar:Human-readable sales order number|DOCSTATUS:char(2):Compiere lifecycle status such as CO completed CL closed DR draft|ISSOTRX:char(1):Y for sales transactions N for purchases|C_BPARTNER_ID:bigint:Customer business partner|DATEORDERED:timestamp:Sales order date|C_DOCTYPE_ID:bigint:Actual document type|C_DOCTYPETARGET_ID:bigint:Target sales document type|M_WAREHOUSE_ID:bigint:Warehouse used by the order|TOTALLINES:numeric:Order net amount before tax|GRANDTOTAL:numeric:Order total amount including tax",
            "C_BPARTNER_ID -> C_BPARTNER (C_BPARTNER_ID)|C_ORDER_ID -> C_ORDERLINE (C_ORDER_ID)|C_ORDER_ID -> C_INVOICE (C_ORDER_ID)|C_ORDER_ID -> M_INOUT (C_ORDER_ID)|C_DOCTYPE_ID -> C_DOCTYPE (C_DOCTYPE_ID)|C_DOCTYPETARGET_ID -> C_DOCTYPE (C_DOCTYPE_ID)|M_WAREHOUSE_ID -> M_WAREHOUSE (M_WAREHOUSE_ID)|AD_ORG_ID -> AD_ORG (AD_ORG_ID)",
            "Use for vente order counts totals statuses document numbers and order dates. Also use with customers who bought products and customer purchase questions through C_BPARTNER and C_ORDERLINE. The imported dataset is already vente scoped with ISSOTRX Y and completed or closed sales documents.",
        ),
        _row(
            "C_ORDERLINE",
            "Sales",
            "Sales order line detail storing products ordered quantities delivered quantities invoiced quantities prices and line net amounts.",
            "Lignes des commandes de vente avec produits quantites commandees livrees facturees prix et montants ligne.",
            "C_ORDERLINE_ID:bigint:Primary sales order line identifier|C_ORDER_ID:bigint:Parent sales order|LINE:bigint:Line sequence|C_BPARTNER_ID:bigint:Customer copied to the line|M_PRODUCT_ID:bigint:Ordered product|M_WAREHOUSE_ID:bigint:Warehouse for fulfillment|QTYORDERED:numeric:Ordered quantity|QTYDELIVERED:numeric:Delivered quantity|QTYINVOICED:numeric:Invoiced quantity|PRICEACTUAL:numeric:Actual unit price|PRICELIST:numeric:List price|LINENETAMT:numeric:Net sales amount for the line|C_TAX_ID:bigint:Tax used on the line",
            "C_ORDER_ID -> C_ORDER (C_ORDER_ID)|M_PRODUCT_ID -> M_PRODUCT (M_PRODUCT_ID)|C_ORDERLINE_ID -> C_INVOICELINE (C_ORDERLINE_ID)|C_ORDERLINE_ID -> M_INOUTLINE (C_ORDERLINE_ID)|C_TAX_ID -> C_TAX (C_TAX_ID)|M_WAREHOUSE_ID -> M_WAREHOUSE (M_WAREHOUSE_ID)",
            "Use for product sales quantities top ordered products line revenue delivered vs ordered and invoiced vs ordered analysis.",
        ),
        _row(
            "C_INVOICE",
            "Finance",
            "Sales invoice header storing customer invoices credit memos invoice dates statuses payment flags and invoice totals.",
            "En-tete des factures client et avoirs avec dates statuts indicateur paye et totaux.",
            "C_INVOICE_ID:bigint:Primary sales invoice identifier|DOCUMENTNO:varchar:Human-readable invoice number|DOCSTATUS:char(2):Invoice lifecycle status|ISSOTRX:char(1):Y for customer invoice|C_DOCTYPE_ID:bigint:Invoice document type such as invoice or credit memo|C_ORDER_ID:bigint:Related sales order when available|C_BPARTNER_ID:bigint:Customer business partner|DATEINVOICED:timestamp:Invoice date|ISPAID:char(1):Whether invoice is marked paid|TOTALLINES:numeric:Invoice net amount before tax|GRANDTOTAL:numeric:Invoice total amount including tax",
            "C_INVOICE_ID -> C_INVOICELINE (C_INVOICE_ID)|C_INVOICE_ID -> C_ALLOCATIONLINE (C_INVOICE_ID)|C_ORDER_ID -> C_ORDER (C_ORDER_ID)|C_BPARTNER_ID -> C_BPARTNER (C_BPARTNER_ID)|C_DOCTYPE_ID -> C_DOCTYPE (C_DOCTYPE_ID)|AD_ORG_ID -> AD_ORG (AD_ORG_ID)",
            "Use for invoice counts revenue by invoice date paid/unpaid flags customer invoicing and credit memo analysis. For actual payment records join through C_ALLOCATIONLINE then C_PAYMENT.",
        ),
        _row(
            "C_INVOICELINE",
            "Finance",
            "Sales invoice line detail storing invoiced products quantities prices taxes and net invoice line amounts.",
            "Lignes des factures client avec produits quantites facturees prix taxes et montants nets.",
            "C_INVOICELINE_ID:bigint:Primary invoice line identifier|C_INVOICE_ID:bigint:Parent invoice header|C_ORDERLINE_ID:bigint:Related sales order line|M_INOUTLINE_ID:bigint:Related delivery line|LINE:bigint:Line sequence|M_PRODUCT_ID:bigint:Invoiced product|QTYINVOICED:numeric:Invoiced quantity|PRICELIST:numeric:List price|PRICEACTUAL:numeric:Actual invoice unit price|LINENETAMT:numeric:Net invoice line amount|C_TAX_ID:bigint:Tax used on invoice line",
            "C_INVOICE_ID -> C_INVOICE (C_INVOICE_ID)|C_ORDERLINE_ID -> C_ORDERLINE (C_ORDERLINE_ID)|M_INOUTLINE_ID -> M_INOUTLINE (M_INOUTLINE_ID)|M_PRODUCT_ID -> M_PRODUCT (M_PRODUCT_ID)|C_TAX_ID -> C_TAX (C_TAX_ID)",
            "Use for revenue by product category tax invoiced quantities and invoice line analysis. Prefer this over order lines for confirmed invoiced sales revenue.",
        ),
        _row(
            "C_BPARTNER",
            "Reference",
            "Business partner master data containing customers suppliers partner codes names groups and commercial flags.",
            "Referentiel des tiers avec clients fournisseurs codes noms groupes et indicateurs commerciaux.",
            "C_BPARTNER_ID:bigint:Primary business partner identifier|VALUE:varchar:Business partner code|NAME:varchar:Business partner display name|NAME2:varchar:Additional partner name|C_BP_GROUP_ID:bigint:Business partner group|ISCUSTOMER:char(1):Whether partner is a customer|ISVENDOR:char(1):Whether partner is a supplier|SO_CREDITLIMIT:numeric:Sales credit limit|SALESREP_ID:bigint:Sales representative",
            "C_BPARTNER_ID -> C_ORDER (C_BPARTNER_ID)|C_BPARTNER_ID -> C_INVOICE (C_BPARTNER_ID)|C_BPARTNER_ID -> C_PAYMENT (C_BPARTNER_ID)|C_BPARTNER_ID -> C_ALLOCATIONLINE (C_BPARTNER_ID)|C_BPARTNER_ID -> M_INOUT (C_BPARTNER_ID)",
            "Use with orders invoices payments and shipments to show real customer names instead of IDs. Also use for customer bought products customer purchases partner purchases and top customers. In this phase the extracted partners are those linked to vente.",
        ),
        _row(
            "M_PRODUCT",
            "Inventory",
            "Product master data for sold and stock-managed products with product codes names categories unit of measure and sale or purchase flags.",
            "Referentiel produits avec codes noms categories unite de mesure et indicateurs vente achat stock.",
            "M_PRODUCT_ID:bigint:Primary product identifier|VALUE:varchar:Product code or article reference|NAME:varchar:Product display name|DESCRIPTION:varchar:Product description|C_UOM_ID:bigint:Unit of measure|M_PRODUCT_CATEGORY_ID:bigint:Product category|PRODUCTTYPE:char(1):Product type|ISSTOCKED:char(1):Whether product is stocked|ISSOLD:char(1):Whether product can be sold|ISPURCHASED:char(1):Whether product can be purchased",
            "M_PRODUCT_ID -> C_ORDERLINE (M_PRODUCT_ID)|M_PRODUCT_ID -> C_INVOICELINE (M_PRODUCT_ID)|M_PRODUCT_ID -> M_INOUTLINE (M_PRODUCT_ID)|M_PRODUCT_ID -> RV_STORAGE (M_PRODUCT_ID)|M_PRODUCT_CATEGORY_ID -> M_PRODUCT_CATEGORY (M_PRODUCT_CATEGORY_ID)",
            "Use to translate product IDs into names and categories. Join to order lines for top products ordered quantity sales quantities revenue delivery and stock questions.",
        ),
        _row(
            "M_PRODUCT_CATEGORY",
            "Inventory",
            "Product category reference table used to group products into families for sales and stock analysis.",
            "Referentiel des categories produits pour regrouper les articles en familles.",
            "M_PRODUCT_CATEGORY_ID:bigint:Primary product category identifier|VALUE:varchar:Category code|NAME:varchar:Category name|DESCRIPTION:varchar:Category description|ISDEFAULT:char(1):Default category flag|PLANNEDMARGIN:numeric:Planned margin percentage",
            "M_PRODUCT_CATEGORY_ID -> M_PRODUCT (M_PRODUCT_CATEGORY_ID)|M_PRODUCT_CATEGORY_ID -> RV_STORAGE (M_PRODUCT_CATEGORY_ID)",
            "Use for revenue by category top product families product mix and category-level stock questions.",
        ),
        _row(
            "C_DOCTYPE",
            "Reference",
            "Document type reference table describing sales orders customer invoices credit memos depots counters markets and document base types.",
            "Referentiel des types documents vente factures avoirs depots comptoirs marches et types de base.",
            "C_DOCTYPE_ID:bigint:Primary document type identifier|NAME:varchar:Document type business name|PRINTNAME:varchar:Printed document label|DESCRIPTION:varchar:Document type description|DOCBASETYPE:varchar:Compiere base document type code|ISSOTRX:char(1):Sales transaction flag|DOCSUBTYPESO:varchar:Sales order subtype when available",
            "C_DOCTYPE_ID -> C_ORDER (C_DOCTYPE_ID)|C_DOCTYPE_ID -> C_ORDER (C_DOCTYPETARGET_ID)|C_DOCTYPE_ID -> C_INVOICE (C_DOCTYPE_ID)|C_DOCTYPE_ID -> M_INOUT (C_DOCTYPE_ID)|C_DOCTYPE_ID -> C_PAYMENT (C_DOCTYPE_ID)",
            "Use to explain document labels such as Facture client Avoir client Commande standard Vente Comptoir Marche and Depot.",
        ),
        _row(
            "C_TAX",
            "Finance",
            "Tax reference table for invoice and order line taxes including VAT names rates and exemption flags.",
            "Referentiel des taxes des lignes commandes et factures avec TVA taux et exemptions.",
            "C_TAX_ID:bigint:Primary tax identifier|NAME:varchar:Tax name such as TVA20 or Livre|DESCRIPTION:varchar:Tax description|RATE:numeric:Tax rate percentage|TAXINDICATOR:varchar:Tax indicator|ISTAXEXEMPT:char(1):Tax exempt flag|C_TAXCATEGORY_ID:bigint:Tax category",
            "C_TAX_ID -> C_ORDERLINE (C_TAX_ID)|C_TAX_ID -> C_INVOICELINE (C_TAX_ID)",
            "Use for tax rate VAT exempt taxable sales and HT versus TTC context when line tax detail is needed.",
        ),
        _row(
            "AD_ORG",
            "Reference",
            "Organization reference table for the legal or operational entity owning the vente transactions.",
            "Referentiel organisationnel de l'entite portant les transactions vente.",
            "AD_ORG_ID:bigint:Primary organization identifier|VALUE:varchar:Organization code|NAME:varchar:Organization name|DESCRIPTION:varchar:Organization description|ISSUMMARY:char(1):Summary organization flag|ISACTIVE:char(1):Active flag",
            "AD_ORG_ID -> C_ORDER (AD_ORG_ID)|AD_ORG_ID -> C_ORDERLINE (AD_ORG_ID)|AD_ORG_ID -> C_INVOICE (AD_ORG_ID)|AD_ORG_ID -> C_INVOICELINE (AD_ORG_ID)|AD_ORG_ID -> M_INOUT (AD_ORG_ID)|AD_ORG_ID -> M_INOUTLINE (AD_ORG_ID)|AD_ORG_ID -> RV_STORAGE (AD_ORG_ID)",
            "Current vente extraction has one organization Stock siege LPN context. Use only when user asks by organization or entity.",
        ),
        _row(
            "C_ALLOCATIONLINE",
            "Finance",
            "Payment allocation line linking customer invoices to payments with allocated amounts discounts write-offs and over-under amounts.",
            "Lignes de lettrage reliant factures client et paiements avec montants affectes remises ecarts et trop peu percu.",
            "C_ALLOCATIONLINE_ID:bigint:Primary allocation line identifier|C_ALLOCATIONHDR_ID:bigint:Parent allocation header|C_INVOICE_ID:bigint:Allocated invoice|C_PAYMENT_ID:bigint:Payment used for the allocation|C_BPARTNER_ID:bigint:Customer business partner|C_ORDER_ID:bigint:Related sales order|DATETRX:timestamp:Allocation transaction date|AMOUNT:numeric:Allocated amount|DISCOUNTAMT:numeric:Discount amount|WRITEOFFAMT:numeric:Write-off amount|OVERUNDERAMT:numeric:Over or under payment amount",
            "C_ALLOCATIONHDR_ID -> C_ALLOCATIONHDR (C_ALLOCATIONHDR_ID)|C_INVOICE_ID -> C_INVOICE (C_INVOICE_ID)|C_PAYMENT_ID -> C_PAYMENT (C_PAYMENT_ID)|C_BPARTNER_ID -> C_BPARTNER (C_BPARTNER_ID)|C_ORDER_ID -> C_ORDER (C_ORDER_ID)",
            "Use this table for reliable invoice-payment joins. Do not join C_PAYMENT directly to C_INVOICE because C_PAYMENT.C_INVOICE_ID is empty in this extraction.",
        ),
        _row(
            "C_ALLOCATIONHDR",
            "Finance",
            "Payment allocation header storing allocation document number dates accounting date status currency and posting state.",
            "En-tete de lettrage paiement avec numero dates statut devise et etat de comptabilisation.",
            "C_ALLOCATIONHDR_ID:bigint:Primary allocation header identifier|DOCUMENTNO:varchar:Allocation document number|DATETRX:timestamp:Allocation transaction date|DATEACCT:timestamp:Accounting date|C_CURRENCY_ID:bigint:Currency identifier|DOCSTATUS:char(2):Allocation document status|POSTED:char(1):Posting state|ISMANUAL:char(1):Manual allocation flag",
            "C_ALLOCATIONHDR_ID -> C_ALLOCATIONLINE (C_ALLOCATIONHDR_ID)|AD_ORG_ID -> AD_ORG (AD_ORG_ID)",
            "Use for payment allocation dates statuses and accounting context after starting from C_ALLOCATIONLINE.",
        ),
        _row(
            "C_PAYMENT",
            "Finance",
            "Customer payment header storing payment numbers dates amounts receipt flag tender type bank account partner and payment status.",
            "En-tete des paiements client avec numeros dates montants mode banque tiers et statut.",
            "C_PAYMENT_ID:bigint:Primary payment identifier|DOCUMENTNO:varchar:Payment document number|DATETRX:timestamp:Payment date|ISRECEIPT:char(1):Y when payment is a customer receipt|C_DOCTYPE_ID:bigint:Payment document type|TRXTYPE:varchar:Transaction type|TENDERTYPE:varchar:Tender or payment method|C_BANKACCOUNT_ID:bigint:Bank account|C_BPARTNER_ID:bigint:Customer business partner|PAYAMT:numeric:Payment amount|DISCOUNTAMT:numeric:Discount amount|WRITEOFFAMT:numeric:Write-off amount|DOCSTATUS:char(2):Payment document status",
            "C_PAYMENT_ID -> C_ALLOCATIONLINE (C_PAYMENT_ID)|C_BPARTNER_ID -> C_BPARTNER (C_BPARTNER_ID)|C_DOCTYPE_ID -> C_DOCTYPE (C_DOCTYPE_ID)",
            "Use with C_ALLOCATIONLINE to answer paid invoices collections payment methods and cash receipt questions. C_INVOICE_ID is empty in C_PAYMENT in this database.",
        ),
        _row(
            "M_INOUT",
            "Sales",
            "Shipment and delivery header for vente orders storing shipment numbers movement dates statuses partners warehouses and related orders.",
            "En-tete des expeditions et livraisons vente avec numeros dates statuts clients entrepots et commandes liees.",
            "M_INOUT_ID:bigint:Primary shipment or delivery identifier|DOCUMENTNO:varchar:Shipment or delivery document number|ISSOTRX:char(1):Y for sales shipment|DOCSTATUS:char(2):Shipment document status|C_DOCTYPE_ID:bigint:Shipment document type|C_ORDER_ID:bigint:Related sales order|DATEORDERED:timestamp:Original order date|MOVEMENTDATE:timestamp:Shipment movement date|DATEACCT:timestamp:Accounting date|C_BPARTNER_ID:bigint:Customer business partner|M_WAREHOUSE_ID:bigint:Warehouse used for shipment",
            "M_INOUT_ID -> M_INOUTLINE (M_INOUT_ID)|C_ORDER_ID -> C_ORDER (C_ORDER_ID)|C_BPARTNER_ID -> C_BPARTNER (C_BPARTNER_ID)|M_WAREHOUSE_ID -> M_WAREHOUSE (M_WAREHOUSE_ID)|C_DOCTYPE_ID -> C_DOCTYPE (C_DOCTYPE_ID)|AD_ORG_ID -> AD_ORG (AD_ORG_ID)",
            "Use for delivery counts shipment dates delivered orders and logistics status. Join to M_INOUTLINE for delivered products and quantities.",
        ),
        _row(
            "M_INOUTLINE",
            "Sales",
            "Shipment and delivery line detail for vente storing delivered products order-line links locators movement quantities and availability flags.",
            "Lignes des expeditions et livraisons vente avec produits liens commande emplacements quantites livrees et disponibilite.",
            "M_INOUTLINE_ID:bigint:Primary shipment line identifier|M_INOUT_ID:bigint:Parent shipment header|LINE:bigint:Shipment line sequence|C_ORDERLINE_ID:bigint:Related sales order line|M_LOCATOR_ID:bigint:Stock locator|M_PRODUCT_ID:bigint:Delivered product|C_UOM_ID:bigint:Unit of measure|MOVEMENTQTY:numeric:Delivered movement quantity|QTYENTERED:numeric:Entered quantity|QTYONHAND:numeric:Stock on hand snapshot on line|QTYRESERVED:numeric:Reserved quantity snapshot on line|DISPONIBILITE:text:Availability indicator from source export",
            "M_INOUT_ID -> M_INOUT (M_INOUT_ID)|C_ORDERLINE_ID -> C_ORDERLINE (C_ORDERLINE_ID)|M_PRODUCT_ID -> M_PRODUCT (M_PRODUCT_ID)|M_LOCATOR_ID -> M_LOCATOR (M_LOCATOR_ID)",
            "Use for delivered quantity by product delivered versus ordered analysis delivery detail and availability signals from shipment lines.",
        ),
        _row(
            "M_WAREHOUSE",
            "Inventory",
            "Warehouse reference table describing stock warehouses used by vente orders shipments and storage availability.",
            "Referentiel des entrepots utilises par les commandes livraisons et stocks vente.",
            "M_WAREHOUSE_ID:bigint:Primary warehouse identifier|VALUE:varchar:Warehouse code|NAME:varchar:Warehouse name|DESCRIPTION:varchar:Warehouse description|C_LOCATION_ID:bigint:Warehouse location|SEPARATOR:varchar:Locator separator",
            "M_WAREHOUSE_ID -> C_ORDER (M_WAREHOUSE_ID)|M_WAREHOUSE_ID -> M_INOUT (M_WAREHOUSE_ID)|M_WAREHOUSE_ID -> M_LOCATOR (M_WAREHOUSE_ID)|M_WAREHOUSE_ID -> RV_STORAGE (M_WAREHOUSE_ID)",
            "Current vente extraction mainly uses Stock siege LPN. Use for warehouse labels and stock location context.",
        ),
        _row(
            "M_LOCATOR",
            "Inventory",
            "Stock locator reference table identifying the exact warehouse locator or storage bin used by shipment lines and stock rows.",
            "Referentiel des emplacements stock dans l'entrepot utilises par livraisons et stock.",
            "M_LOCATOR_ID:bigint:Primary stock locator identifier|VALUE:varchar:Locator name or code|M_WAREHOUSE_ID:bigint:Parent warehouse|PRIORITYNO:bigint:Locator priority|ISDEFAULT:char(1):Default locator flag|X:varchar:Locator X coordinate|Y:varchar:Locator Y coordinate|Z:varchar:Locator Z coordinate",
            "M_LOCATOR_ID -> M_INOUTLINE (M_LOCATOR_ID)|M_LOCATOR_ID -> RV_STORAGE (M_LOCATOR_ID)|M_WAREHOUSE_ID -> M_WAREHOUSE (M_WAREHOUSE_ID)",
            "Use only when questions need exact stock location. Current vente extraction has one main locator Entrepot.",
        ),
        _row(
            "RV_STORAGE",
            "Inventory",
            "Stock availability view for vente products with product labels warehouse locator on-hand reserved available and ordered quantities.",
            "Vue de disponibilite stock des produits vente avec libelles produit entrepot emplacement stock reserve disponible et commande.",
            "M_PRODUCT_ID:bigint:Product identifier|VALUE:varchar:Product code|NAME:varchar:Product name|M_PRODUCT_CATEGORY_ID:bigint:Product category|M_LOCATOR_ID:bigint:Stock locator|M_WAREHOUSE_ID:bigint:Warehouse|QTYONHAND:numeric:Quantity physically on hand|QTYRESERVED:numeric:Reserved quantity|QTYAVAILABLE:numeric:Available quantity after reservations|QTYORDERED:numeric:Quantity on purchase or replenishment order|DATELASTINVENTORY:timestamp:Last inventory date|M_ATTRIBUTESETINSTANCE_ID:bigint:Attribute set instance",
            "M_PRODUCT_ID -> M_PRODUCT (M_PRODUCT_ID)|M_PRODUCT_CATEGORY_ID -> M_PRODUCT_CATEGORY (M_PRODUCT_CATEGORY_ID)|M_LOCATOR_ID -> M_LOCATOR (M_LOCATOR_ID)|M_WAREHOUSE_ID -> M_WAREHOUSE (M_WAREHOUSE_ID)|AD_ORG_ID -> AD_ORG (AD_ORG_ID)",
            "Use for stock availability rupture risk on-hand reserved available and ordered stock questions for products present in the vente extraction.",
        ),
    ]


def _row(
    table_name: str,
    module: str,
    description_en: str,
    description_fr: str,
    key_columns: str,
    relations: str,
    notes: str,
) -> dict[str, str]:
    return {
        "table_name": table_name,
        "module": module,
        "description_en": description_en,
        "description_fr": description_fr,
        "key_columns": key_columns,
        "relations": relations,
        "sensitive": "false",
        "notes": notes,
    }


def _validate_written_csv() -> None:
    with SCHEMA_METADATA_PATH.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))

    if len(rows) != len(EXPECTED_TABLES):
        raise SystemExit(f"Expected {len(EXPECTED_TABLES)} rows but wrote {len(rows)}")

    for row in rows:
        missing = [field for field in FIELDNAMES if field not in row]
        if missing:
            raise SystemExit(f"{row.get('table_name', '<unknown>')} missing fields: {missing}")
        required_empty = [
            field
            for field in ("table_name", "module", "description_en", "key_columns", "sensitive")
            if not row[field].strip()
        ]
        if required_empty:
            raise SystemExit(f"{row['table_name']} has empty required fields: {required_empty}")


if __name__ == "__main__":
    main()
