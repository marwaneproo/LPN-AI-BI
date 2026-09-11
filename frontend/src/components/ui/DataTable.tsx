import type { DataRow } from "../../types/common.types";
import { formatCell } from "../../utils/formatters";

export function DataTable({ rows }: { rows: DataRow[] }) {
  const columns = Object.keys(rows[0] ?? {});
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column}>{column}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={index}>
              {columns.map((column) => (
                <td key={column}>{formatCell(row[column])}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function DataPreview({ rows }: { rows: DataRow[] }) {
  if (!rows.length) {
    return (
      <div className="empty-inline">
        Aucune ligne retournée par l'exécution SQL.
      </div>
    );
  }
  return <DataTable rows={rows} />;
}
