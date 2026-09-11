import { ChevronRight } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

export function InteractiveChartCard({
  chartId,
  children,
  className = "",
}: {
  chartId: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Link className={`card interactive-chart-card ${className}`} to={`/tableau-de-bord/${chartId}`} aria-label="Ouvrir le détail du graphique">
      <span className="chart-open-indicator">
        Voir détail
        <ChevronRight size={13} />
      </span>
      {children}
    </Link>
  );
}
