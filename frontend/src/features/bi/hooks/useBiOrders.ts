import { useEffect, useMemo, useState } from "react";
import { fetchBiOrders } from "../api/biApi";
import type { BiOrdersFilters, BiOrdersResponse, RankedBarDatum } from "../types/bi.types";
import { formatCompactMoney, formatNumber, formatPercent, shortenLabel } from "../../../utils/formatters";

const TYPE_COLORS = ["var(--chart-blue)", "var(--chart-green)", "var(--bi-pink)", "var(--chart-amber)", "var(--chart-violet)"];

function clampPercent(value: number | null | undefined) {
  return Math.max(0, Math.min(100, Number(value ?? 0)));
}

export function useBiOrders(filters: BiOrdersFilters = {}) {
  const [snapshot, setSnapshot] = useState<BiOrdersResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  async function refreshOrders() {
    setIsLoading(true);
    setError("");
    try {
      setSnapshot(await fetchBiOrders(filters));
    } catch (error) {
      setError(error instanceof Error ? error.message : "Commandes BI indisponibles.");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void refreshOrders();
  }, [filters.compare, filters.from, filters.granularity, filters.to, filters.commercial]);

  const kpis = snapshot?.data.kpis;
  const typeItems = useMemo(
    () =>
      (snapshot?.data.by_type ?? []).slice(0, 6).map((type, index) => ({
        label: type.order_type_name,
        value: Number(type.order_count ?? 0),
        color: TYPE_COLORS[index % TYPE_COLORS.length],
      })),
    [snapshot],
  );
  const commercialFlowBars = useMemo(() => {
    const topCommercial = snapshot?.data.commercial_flow?.[0];
    return [
      {
        label: "Couverture CA",
        value: clampPercent(kpis?.amount_invoice_coverage_percent),
        color: "var(--chart-blue)",
      },
      {
        label: "Livré top",
        value: clampPercent(topCommercial?.delivered_coverage_percent),
        color: "var(--chart-green)",
      },
      {
        label: "Facturé top",
        value: clampPercent(topCommercial?.invoice_coverage_percent),
        color: "var(--bi-pink)",
      },
    ];
  }, [kpis, snapshot]);
  const controlSignals = useMemo(() => {
    const topType = snapshot?.data.by_type?.[0];
    const topCommercial = snapshot?.data.commercial_flow?.[0];
    const topStatus = snapshot?.data.status_breakdown?.[0];
    return [
      {
        label: "Par commercial",
        value: topCommercial?.commercial_label || "Indisponible",
        helper: topCommercial ? `${formatNumber(topCommercial.order_count)} commandes · ${formatCompactMoney(topCommercial.ordered_sales)}` : "Aucun commercial disponible",
      },
      {
        label: "Par type",
        value: topType?.order_type_name || "Indisponible",
        helper: topType ? `${formatNumber(topType.order_count)} commandes · ${formatCompactMoney(topType.ordered_sales)}` : "Aucun type disponible",
      },
      {
        label: "Par statut",
        value: topStatus?.label || "Indisponible",
        helper: topStatus ? `${formatNumber(topStatus.item_count)} commandes · ${formatCompactMoney(topStatus.total_value)}` : "Aucun statut disponible",
      },
    ];
  }, [snapshot]);

  const comparisonCards = useMemo(() => {
    const years = [...(snapshot?.data.yearly ?? [])].sort((a, b) => a.year - b.year);
    if (!years.length) return [];
    const current = years[years.length - 1];
    const previous = years.length > 1 ? years[years.length - 2] : null;
    const prevYear = previous?.year ?? current.year;
    const basket = (point: typeof current | null) =>
      point && point.order_count > 0 ? Number(point.order_value || 0) / point.order_count : 0;

    return [
      {
        key: "commandes",
        label: "Commandes",
        tone: "blue" as const,
        value: formatNumber(current.order_count),
        helper: `Volume ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.order_count || 0),
        previousValue: Number(previous?.order_count ?? 0),
      },
      {
        key: "ca",
        label: "CA commandé",
        tone: "green" as const,
        value: formatCompactMoney(current.order_value),
        helper: `Commandé ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.order_value || 0),
        previousValue: Number(previous?.order_value ?? 0),
      },
      {
        key: "facturees",
        label: "Commandes facturées",
        tone: "amber" as const,
        value: formatPercent(current.invoice_coverage_percent),
        helper: `Taux facturation ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.invoice_coverage_percent || 0),
        previousValue: Number(previous?.invoice_coverage_percent ?? 0),
      },
      {
        key: "livraison",
        label: "Couverture livraison",
        tone: "pink" as const,
        value: formatPercent(current.delivery_coverage_percent),
        helper: `Taux livraison ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.delivery_coverage_percent || 0),
        previousValue: Number(previous?.delivery_coverage_percent ?? 0),
      },
      {
        key: "panier",
        label: "Panier moyen",
        tone: "violet" as const,
        value: formatCompactMoney(basket(current)),
        helper: `Par commande ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: basket(current),
        previousValue: basket(previous),
      },
    ];
  }, [snapshot]);

  const typeBars = useMemo<RankedBarDatum[]>(
    () =>
      (snapshot?.data.by_type ?? []).slice(0, 8).map((type, index) => ({
        id: `type-${index}`,
        name: shortenLabel(type.order_type_name, 22),
        fullName: type.order_type_name,
        value: Number(type.ordered_sales ?? 0),
        meta: `${formatNumber(type.order_count)} commandes`,
      })),
    [snapshot],
  );

  const commercialBars = useMemo<RankedBarDatum[]>(
    () =>
      (snapshot?.data.commercial_flow ?? []).slice(0, 8).map((flow, index) => ({
        id: `flow-${index}`,
        name: shortenLabel(flow.commercial_label, 20),
        fullName: flow.commercial_label,
        value: Number(flow.ordered_sales ?? 0),
        meta: `${formatNumber(flow.order_count)} cmd · ${formatPercent(flow.invoice_coverage_percent)} facturé`,
      })),
    [snapshot],
  );

  const commercialReadings = useMemo(() => {
    const rows = snapshot?.data.commercial_flow ?? [];
    const maxSales = Math.max(...rows.map((flow) => Number(flow.ordered_sales ?? 0)), 1);
    return rows.slice(0, 6).map((flow, index) => ({
      id: flow.salesrep_id || `flow-${index}`,
      // Real salesrep_id when the row maps to an actual commercial — this is what
      // the click-to-filter sends as the `commercial` query param.
      salesrepId: Number.isFinite(Number(flow.salesrep_id)) && Number(flow.salesrep_id) > 0 ? Number(flow.salesrep_id) : null,
      label: flow.commercial_label || "Sans commercial",
      meta: `${formatNumber(flow.order_count)} commandes`,
      orderedSales: formatCompactMoney(Number(flow.ordered_sales ?? 0)),
      widthPct: (Number(flow.ordered_sales ?? 0) / maxSales) * 100,
      deliveredPercent: Number(flow.delivered_coverage_percent ?? 0),
      invoicedPercent: Number(flow.invoice_coverage_percent ?? 0),
    }));
  }, [snapshot]);

  const funnel = snapshot?.data.funnel;
  const hasFunnel = Number(funnel?.quantity_ordered ?? 0) > 0;

  const statusInsights = useMemo(() => {
    const accents = ["var(--chart-blue)", "var(--chart-green)", "var(--chart-amber)", "var(--bi-pink)", "var(--chart-violet)"];
    return (snapshot?.data.status_breakdown ?? []).slice(0, 6).map((status, index) => ({
      label: `${formatNumber(status.item_count)} commandes`,
      title: status.label || status.status,
      value: formatCompactMoney(status.total_value),
      helper: "CA commandé",
      accent: accents[index % accents.length],
    }));
  }, [snapshot]);

  return {
    snapshot,
    isLoading,
    error,
    refreshOrders,
    kpis,
    typeItems,
    commercialFlowBars,
    controlSignals,
    typeBars,
    commercialBars,
    commercialReadings,
    statusInsights,
    comparisonCards,
    funnel,
    hasFunnel,
  };
}
