-- Compiere export for C_ORDER
-- Run on company PC against Oracle 11g.
-- Save output as CSV with header row, UTF-8 encoding.
SELECT *
FROM C_ORDER
WHERE Created >= TO_DATE('2021-01-01','YYYY-MM-DD');

