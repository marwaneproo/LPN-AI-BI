import { useMemo, useState } from "react";
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
import type { BiDetailColumn, BiDetailRow } from "../types/bi.types";
import { formatCompactMoney, formatMoneyFull, formatNumber, formatPercent } from "../../../utils/formatters";

type SortDirection = "asc" | "desc";

function formatCell(value: string | number | boolean | null | undefined, format?: BiDetailColumn["format"]) {
  if (value === null || value === undefined || value === "") return "—";
  switch (format) {
    case "money":
      return formatMoneyFull(Number(value));
    case "moneyCompact":
      return formatCompactMoney(Number(value));
    case "percent":
      return formatPercent(Number(value));
    case "number":
      return formatNumber(Number(value));
    default:
      return String(value);
  }
}

/** Semantic sortable table for BiDetailDrawer: click a header to toggle asc/desc, sticky header, no library. */
export function BiDataTable({ columns, rows }: { columns: BiDetailColumn[]; rows: BiDetailRow[] }) {
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortDirection, setSortDirection] = useState<SortDirection>("desc");

  const sortedRows = useMemo(() => {
    if (!sortKey) return rows;
    const copy = [...rows];
    copy.sort((a, b) => {
      const rawA = a[sortKey];
      const rawB = b[sortKey];
      const numA = typeof rawA === "number" ? rawA : Number(rawA);
      const numB = typeof rawB === "number" ? rawB : Number(rawB);
      const bothNumeric = rawA !== null && rawA !== undefined && rawB !== null && rawB !== undefined && Number.isFinite(numA) && Number.isFinite(numB);
      const comparison = bothNumeric
        ? numA - numB
        : String(rawA ?? "").localeCompare(String(rawB ?? ""), "fr", { sensitivity: "base" });
      return sortDirection === "asc" ? comparison : -comparison;
    });
    return copy;
  }, [rows, sortKey, sortDirection]);

  const handleSort = (key: string) => {
    if (sortKey === key) {
      setSortDirection((direction) => (direction === "asc" ? "desc" : "asc"));
      return;
    }
    setSortKey(key);
    setSortDirection("desc");
  };

  if (!rows.length) {
    return <p className="bi-data-table-empty">Aucune donnée à afficher.</p>;
  }

  return (
    <div className="table-wrap bi-data-table">
      <table>
        <thead>
          <tr>
            {columns.map((column) => {
              const active = sortKey === column.key;
              return (
                <th key={column.key} aria-sort={active ? (sortDirection === "asc" ? "ascending" : "descending") : "none"}>
                  <button type="button" className={`bi-data-table-sort${active ? " is-active" : ""}`} onClick={() => handleSort(column.key)}>
                    {column.label}
                    {active ? (
                      sortDirection === "asc" ? (
                        <ArrowUp size={12} aria-hidden="true" />
                      ) : (
                        <ArrowDown size={12} aria-hidden="true" />
                      )
                    ) : (
                      <ArrowUpDown size={12} className="bi-data-table-sort-idle" aria-hidden="true" />
                    )}
                  </button>
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {sortedRows.map((row, index) => (
            <tr key={index}>
              {columns.map((column) => (
                <td key={column.key}>{formatCell(row[column.key], column.format)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
