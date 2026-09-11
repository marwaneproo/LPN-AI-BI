/* LPN AI-BI - Vente 2025 + 2026 fix-up exports
   Run only these if Oracle is still available.
   These are small replacements/fixes, not a full re-export.
*/

/* 03B_M_INOUT_2025_2026_BY_MOVEMENTDATE.xlsx
   Replacement for 03_M_INOUT_2025_2026.xlsx.
   This matches the M_INOUTLINE quarter chunks, which are movement-date scoped.
*/
SELECT io.*
FROM M_INOUT io
JOIN C_ORDER o ON o.C_ORDER_ID = io.C_ORDER_ID
WHERE io.MOVEMENTDATE >= DATE '2025-01-01'
  AND io.MOVEMENTDATE < DATE '2026-07-01'
  AND io.ISSOTRX = 'Y'
  AND io.DOCSTATUS IN ('CO', 'CL')
  AND o.ISSOTRX = 'Y'
  AND o.DOCSTATUS IN ('CO', 'CL')
  AND o.C_DOCTYPETARGET_ID IN (1000028, 1000034, 1000032, 1000031)
ORDER BY io.MOVEMENTDATE, io.M_INOUT_ID;

/* 26B_M_PRODUCT_2025_2026_WITH_DELIVERY_PRODUCTS.xlsx
   Replacement for 26_M_PRODUCT_2025_2026.xlsx.
   Adds products that appear only in delivery lines.
*/
WITH selected_products AS (
    SELECT /*+ MATERIALIZE */ DISTINCT ol.M_PRODUCT_ID
    FROM C_ORDERLINE ol
    JOIN C_ORDER o ON o.C_ORDER_ID = ol.C_ORDER_ID
    WHERE o.DATEORDERED >= DATE '2025-01-01'
      AND o.DATEORDERED < DATE '2026-07-01'
      AND o.ISSOTRX = 'Y'
      AND o.DOCSTATUS IN ('CO', 'CL')
      AND o.C_DOCTYPETARGET_ID IN (1000028, 1000034, 1000032, 1000031)
      AND ol.M_PRODUCT_ID IS NOT NULL
    UNION
    SELECT DISTINCT il.M_PRODUCT_ID
    FROM C_INVOICELINE il
    JOIN C_INVOICE i ON i.C_INVOICE_ID = il.C_INVOICE_ID
    WHERE i.DATEINVOICED >= DATE '2025-01-01'
      AND i.DATEINVOICED < DATE '2026-07-01'
      AND i.ISSOTRX = 'Y'
      AND i.DOCSTATUS = 'CO'
      AND i.C_DOCTYPE_ID IN (1000002, 1000003, 1000004)
      AND il.M_PRODUCT_ID IS NOT NULL
    UNION
    SELECT DISTINCT iol.M_PRODUCT_ID
    FROM M_INOUTLINE iol
    JOIN M_INOUT io ON io.M_INOUT_ID = iol.M_INOUT_ID
    JOIN C_ORDER o ON o.C_ORDER_ID = io.C_ORDER_ID
    WHERE io.MOVEMENTDATE >= DATE '2025-01-01'
      AND io.MOVEMENTDATE < DATE '2026-07-01'
      AND io.ISSOTRX = 'Y'
      AND io.DOCSTATUS IN ('CO', 'CL')
      AND o.ISSOTRX = 'Y'
      AND o.DOCSTATUS IN ('CO', 'CL')
      AND o.C_DOCTYPETARGET_ID IN (1000028, 1000034, 1000032, 1000031)
      AND iol.M_PRODUCT_ID IS NOT NULL
)
SELECT p.*
FROM M_PRODUCT p
JOIN selected_products sp ON sp.M_PRODUCT_ID = p.M_PRODUCT_ID
ORDER BY p.M_PRODUCT_ID;

/* Optional: re-run 24_C_PAYMENT_2025_2026.xlsx with "Include column headers" enabled in Toad.
   The previous data is usable, but it was exported without headers.
*/
WITH selected_payments AS (
    SELECT /*+ MATERIALIZE */ DISTINCT al.C_PAYMENT_ID
    FROM C_ALLOCATIONLINE al
    JOIN C_INVOICE i ON i.C_INVOICE_ID = al.C_INVOICE_ID
    WHERE i.DATEINVOICED >= DATE '2025-01-01'
      AND i.DATEINVOICED < DATE '2026-07-01'
      AND i.ISSOTRX = 'Y'
      AND i.DOCSTATUS = 'CO'
      AND i.C_DOCTYPE_ID IN (1000002, 1000003, 1000004)
      AND al.C_PAYMENT_ID IS NOT NULL
)
SELECT p.*
FROM C_PAYMENT p
JOIN selected_payments sp ON sp.C_PAYMENT_ID = p.C_PAYMENT_ID
ORDER BY p.DATETRX, p.C_PAYMENT_ID;

/* Optional: re-run 38_M_LOCATOR_2025_2026.xlsx with "Include column headers" enabled in Toad.
   The previous data is usable, but it was exported without headers.
*/
WITH selected_locators AS (
    SELECT /*+ MATERIALIZE */ DISTINCT iol.M_LOCATOR_ID
    FROM M_INOUTLINE iol
    JOIN M_INOUT io ON io.M_INOUT_ID = iol.M_INOUT_ID
    WHERE io.MOVEMENTDATE >= DATE '2025-01-01'
      AND io.MOVEMENTDATE < DATE '2026-07-01'
      AND io.ISSOTRX = 'Y'
      AND io.DOCSTATUS IN ('CO', 'CL')
      AND iol.M_LOCATOR_ID IS NOT NULL
)
SELECT loc.*
FROM M_LOCATOR loc
JOIN selected_locators sl ON sl.M_LOCATOR_ID = loc.M_LOCATOR_ID
ORDER BY loc.M_LOCATOR_ID;
