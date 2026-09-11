import type { BiDetailColumn, BiDetailRow } from "../types/bi.types";
import { formatCompactMoney, formatMoneyFull, formatNumber, formatPercent } from "../../../utils/formatters";

const UTF8_BOM = String.fromCharCode(0xfeff);

function csvCell(value: string | number | boolean | null | undefined, format?: BiDetailColumn["format"]) {
  if (value === null || value === undefined || value === "") return "";
  let text: string;
  switch (format) {
    case "money":
      text = formatMoneyFull(Number(value));
      break;
    case "moneyCompact":
      text = formatCompactMoney(Number(value));
      break;
    case "percent":
      text = formatPercent(Number(value));
      break;
    case "number":
      text = formatNumber(Number(value));
      break;
    default:
      text = String(value);
  }
  const escaped = text.replace(/"/g, '""');
  return /[;"\n]/.test(escaped) ? `"${escaped}"` : escaped;
}

/** Semicolon-separated (French Excel convention), CRLF line endings. */
export function buildBiCsv(columns: BiDetailColumn[], rows: BiDetailRow[]) {
  const header = columns.map((column) => csvCell(column.label)).join(";");
  const lines = rows.map((row) => columns.map((column) => csvCell(row[column.key], column.format)).join(";"));
  return [header, ...lines].join("\r\n");
}

/** Triggers a browser download of `rows` as CSV, UTF-8 BOM so Excel keeps French accents intact. */
export function downloadBiCsv(columns: BiDetailColumn[], rows: BiDetailRow[], filename: string) {
  const csv = UTF8_BOM + buildBiCsv(columns, rows);
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/** Builds the `lpn-bi-<page>-<widget>-<from>-<to>.csv` filename convention shared by every drawer. */
export function biCsvFilename(pageSlug: string, widgetSlug: string, from: string, to: string) {
  return `lpn-bi-${pageSlug}-${widgetSlug}-${from}-${to}.csv`;
}
