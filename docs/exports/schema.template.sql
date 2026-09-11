/*
PostgreSQL schema template for Compiere snapshot bundles.

Write CREATE TABLE statements that match the exported CSV files exactly.
Use PostgreSQL types, not Oracle types.

Type mapping rules:
- NUMBER(10) or ID columns -> bigint
- NUMBER(p, s) where s > 0 -> numeric(p, s)
- NUMBER without known precision -> numeric
- NVARCHAR2(n), VARCHAR2(n), CHAR(n) -> varchar(n)
- DATE -> timestamp
- CLOB -> text
- Y/N flags -> char(1)

Keep original Compiere table and column names, but lowercase them in PostgreSQL
for consistency with generated SQL and psql conventions.
*/

CREATE SCHEMA IF NOT EXISTS business;

DROP TABLE IF EXISTS business.c_order;

CREATE TABLE business.c_order (
  c_order_id bigint PRIMARY KEY,
  ad_client_id bigint NOT NULL,
  ad_org_id bigint NOT NULL,
  isactive char(1),
  created timestamp,
  createdby bigint,
  updated timestamp,
  updatedby bigint,
  documentno varchar(30),
  docstatus char(2),
  docaction char(2),
  c_doctype_id bigint,
  c_doctypetarget_id bigint,
  c_bpartner_id bigint,
  c_bpartner_location_id bigint,
  dateordered timestamp,
  datepromised timestamp,
  datedelivered timestamp,
  dateinvoiced timestamp,
  issotrx char(1),
  salesrep_id bigint,
  m_warehouse_id bigint,
  c_currency_id bigint,
  paymentrule char(1),
  c_paymentterm_id bigint,
  totallines numeric(18, 2),
  grandtotal numeric(18, 2),
  processed char(1),
  processing char(1),
  description text
);

