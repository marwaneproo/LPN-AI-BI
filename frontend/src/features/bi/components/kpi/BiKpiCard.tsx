import type { ReactNode } from "react";
import { Card } from "../../../../components/ui/Card";

export function BiKpiCard({
  label,
  value,
  helper,
  icon,
  good = false,
  strong = false,
}: {
  label: string;
  value: string;
  helper: string;
  icon: ReactNode;
  good?: boolean;
  strong?: boolean;
}) {
  const className = [
    "bi-kpi-card",
    strong ? "primary-bi-kpi" : "",
    good ? "positive-bi-kpi" : "",
  ].filter(Boolean).join(" ");

  return (
    <Card className={className}>
      <div className="bi-kpi-top">
        <span>{label}</span>
        {icon}
      </div>
      <strong>{value}</strong>
      <small className={good ? "positive" : ""}>{helper}</small>
    </Card>
  );
}
