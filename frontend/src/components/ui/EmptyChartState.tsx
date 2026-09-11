import { BarChart3 } from "lucide-react";

export function EmptyChartState({ text, hint }: { text: string; hint?: string }) {
  return (
    <div className="empty-chart-state">
      <BarChart3 size={20} aria-hidden="true" />
      <span>{text}</span>
      {hint ? <small>{hint}</small> : null}
    </div>
  );
}
