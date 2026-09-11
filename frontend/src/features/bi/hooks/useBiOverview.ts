import { useEffect, useMemo, useState } from "react";
import { fetchBiOverview } from "../api/biApi";
import type {
  BiNamedMetric,
  BiOverviewFilters,
  BiOverviewResponse,
  FormattedAnalysisTrendPoint,
  RankedBarDatum,
} from "../types/bi.types";
import { formatAnalysisPeriodLabel, formatPeriodLabel } from "../utils/biFormatters";
import { formatCompactMoney, formatNumber, formatPercent, shortenLabel } from "../../../utils/formatters";

const TREND_COLORS = ["var(--bi-pink)", "var(--chart-blue)", "var(--chart-green)", "var(--chart-amber)", "var(--chart-violet)"];

function metricName(metric: BiNamedMetric | null | undefined) {
  return metric?.name || "Indisponible";
}

function metricHelper(metric: BiNamedMetric | null | undefined, label: string) {
  return metric ? `${label}: ${formatCompactMoney(metric.value)}` : "Aucune valeur disponible";
}

export function useBiOverview(filters: BiOverviewFilters = {}) {
  const [snapshot, setSnapshot] = useState<BiOverviewResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  async function refreshOverview() {
    setIsLoading(true);
    setError("");
    try {
      setSnapshot(await fetchBiOverview(filters));
    } catch (error) {
      setError(error instanceof Error ? error.message : "Vue d'ensemble BI indisponible.");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void refreshOverview();
  }, [filters.compare, filters.from, filters.granularity, filters.to]);

  const kpis = snapshot?.data.kpis;
  const trendItems = useMemo(
    () =>
      (snapshot?.data.trend ?? []).slice(-6).map((point, index) => ({
        label: formatPeriodLabel(point.period),
        value: Number(point.order_value ?? 0),
        color: TREND_COLORS[index % TREND_COLORS.length],
      })),
    [snapshot],
  );
  const quickSignals = useMemo(() => {
    const signals = snapshot?.data.quick_signals;
    return [
      {
        label: "Commercial dominant",
        value: metricName(signals?.top_commercial),
        helper: metricHelper(signals?.top_commercial, "CA facturé"),
      },
      {
        label: "Fournisseur moteur",
        value: metricName(signals?.top_supplier),
        helper: metricHelper(signals?.top_supplier, "CA facturé"),
      },
      {
        label: "Zone à suivre",
        value: metricName(signals?.top_region),
        helper: metricHelper(signals?.top_region, "CA facturé"),
      },
    ];
  }, [snapshot]);
  const salesMixSignals = useMemo(() => {
    const mix = snapshot?.data.sales_mix;
    return [
      {
        label: "Facturé",
        value: formatCompactMoney(mix?.invoiced_sales ?? 0),
        helper: "CA facturé depuis mart_sales_monthly",
      },
      {
        label: "Commandé",
        value: formatCompactMoney(mix?.order_value ?? 0),
        helper: "Ventes commandées depuis mart_sales_monthly",
      },
      {
        label: "Articles suivis",
        value: formatNumber(mix?.tracked_products ?? 0),
        helper: "Produits actifs depuis mart_sales_by_product",
      },
    ];
  }, [snapshot]);

  const comparisonCards = useMemo(() => {
    const years = [...(snapshot?.data.yearly ?? [])].sort((a, b) => a.year - b.year);
    if (!years.length) return [];
    const current = years[years.length - 1];
    const previous = years.length > 1 ? years[years.length - 2] : null;
    const prevYear = previous?.year ?? current.year;

    return [
      {
        key: "facture",
        label: "CA facturé",
        tone: "green" as const,
        value: formatCompactMoney(current.invoiced_sales),
        helper: `Facturé ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.invoiced_sales || 0),
        previousValue: Number(previous?.invoiced_sales ?? 0),
      },
      {
        key: "commande",
        label: "Ventes commandées",
        tone: "blue" as const,
        value: formatCompactMoney(current.order_value),
        helper: `Commandé ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.order_value || 0),
        previousValue: Number(previous?.order_value ?? 0),
      },
      {
        key: "clients",
        label: "Clients actifs",
        tone: "pink" as const,
        value: formatNumber(current.active_customers),
        helper: `Clients facturés ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.active_customers || 0),
        previousValue: Number(previous?.active_customers ?? 0),
      },
      {
        key: "couverture",
        label: "Couverture facture",
        tone: "amber" as const,
        value: formatPercent(current.invoice_coverage_percent),
        helper: `Commandes → factures ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.invoice_coverage_percent || 0),
        previousValue: Number(previous?.invoice_coverage_percent ?? 0),
      },
      {
        key: "commandes",
        label: "Commandes",
        tone: "violet" as const,
        value: formatNumber(current.order_count),
        helper: `Nombre de commandes ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.order_count || 0),
        previousValue: Number(previous?.order_count ?? 0),
      },
    ];
  }, [snapshot]);

  const trendPoints = useMemo<FormattedAnalysisTrendPoint[]>(() => {
    // The backend labels compare_trend with the CURRENT window's periods, so the
    // two series merge on `period` without any date arithmetic here.
    const previousByPeriod = new Map((snapshot?.data.compare_trend ?? []).map((point) => [point.period, point]));
    return (snapshot?.data.trend ?? []).map((point) => {
      const previous = previousByPeriod.get(point.period);
      return {
        label: formatAnalysisPeriodLabel(point.period),
        period: point.period,
        orderCount: Number(point.order_count ?? 0),
        orderedSales: Number(point.order_value ?? 0),
        invoiceCount: Number(point.invoice_count ?? 0),
        invoicedSales: Number(point.invoiced_sales ?? 0),
        ...(previous
          ? {
              prevOrderedSales: Number(previous.order_value ?? 0),
              prevInvoicedSales: Number(previous.invoiced_sales ?? 0),
            }
          : {}),
      };
    });
  }, [snapshot]);

  const topCustomerBars = useMemo<RankedBarDatum[]>(
    () =>
      (snapshot?.data.top_customers ?? []).slice(0, 7).map((customer, index) => ({
        id: `customer-${index}`,
        name: shortenLabel(customer.customer_name, 22),
        fullName: customer.customer_name,
        value: Number(customer.total_order_value ?? 0),
        meta: `${formatNumber(customer.order_count)} commandes · panier ${formatCompactMoney(customer.average_order_value)}`,
      })),
    [snapshot],
  );

  const topProductBars = useMemo<RankedBarDatum[]>(
    () =>
      (snapshot?.data.top_products ?? []).slice(0, 8).map((product, index) => ({
        id: `product-${index}`,
        name: shortenLabel(product.product_name, 22),
        fullName: product.product_name,
        value: Number(product.total_order_value ?? 0),
        meta: `${formatNumber(product.total_quantity)} unités · ${formatNumber(product.line_count)} lignes`,
      })),
    [snapshot],
  );

  // Alert rail: at most 3 alerts, computed only from the already-fetched overview payload
  // (kpis + sales_mix + top_customers) — never from another page's endpoint.
  const alerts = useMemo(() => {
    if (!snapshot) return [];
    const data = snapshot.data;
    const found: { id: string; severity: "critical" | "warning"; title: string; detail: string; value: string }[] = [];

    const coverage = Number(data.kpis.invoice_coverage_percent ?? 0);
    if (coverage < 50) {
      found.push({
        id: "coverage",
        severity: coverage < 30 ? "critical" : "warning",
        title: "Couverture facture faible",
        detail: `${formatPercent(coverage)} des commandes de la période sont facturées (seuil de vigilance : 50 %).`,
        value: formatPercent(coverage),
      });
    }

    const invoiceCount = Number(data.kpis.invoice_count ?? 0);
    const unpaidCount = Number(data.kpis.unpaid_invoice_count ?? 0);
    const unpaidRate = invoiceCount > 0 ? (unpaidCount / invoiceCount) * 100 : 0;
    if (unpaidRate > 20) {
      found.push({
        id: "unpaid",
        severity: unpaidRate > 50 ? "critical" : "warning",
        title: "Factures impayées élevées",
        detail: `${formatNumber(unpaidCount)} factures sur ${formatNumber(invoiceCount)} restent impayées sur la période.`,
        value: formatPercent(unpaidRate),
      });
    }

    const topCustomer = data.top_customers?.[0];
    const totalOrdered = Number(data.sales_mix?.order_value ?? 0);
    const topShare = topCustomer && totalOrdered > 0 ? (Number(topCustomer.total_order_value ?? 0) / totalOrdered) * 100 : 0;
    if (topCustomer && topShare >= 20) {
      found.push({
        id: "concentration",
        severity: topShare >= 35 ? "critical" : "warning",
        title: "CA concentré sur un client",
        detail: `${topCustomer.customer_name} pèse ${formatPercent(topShare)} du CA commandé de la période.`,
        value: formatPercent(topShare),
      });
    }

    const rank = { critical: 0, warning: 1 } as const;
    return found.sort((a, b) => rank[a.severity] - rank[b.severity]).slice(0, 3);
  }, [snapshot]);

  const quickInsights = useMemo(() => {
    const signals = snapshot?.data.quick_signals;
    return [
      {
        label: "Commercial moteur",
        title: metricName(signals?.top_commercial),
        value: signals?.top_commercial ? formatCompactMoney(signals.top_commercial.value) : "—",
        helper: "CA facturé",
        accent: "var(--chart-blue)",
      },
      {
        label: "Fournisseur clé",
        title: metricName(signals?.top_supplier),
        value: signals?.top_supplier ? formatCompactMoney(signals.top_supplier.value) : "—",
        helper: "CA facturé",
        accent: "var(--chart-green)",
      },
      {
        label: "Zone à suivre",
        title: metricName(signals?.top_region),
        value: signals?.top_region ? formatCompactMoney(signals.top_region.value) : "—",
        helper: "CA facturé",
        accent: "var(--bi-pink)",
      },
    ];
  }, [snapshot]);

  return {
    snapshot,
    isLoading,
    error,
    refreshOverview,
    kpis,
    trendItems,
    quickSignals,
    salesMixSignals,
    trendPoints,
    topCustomerBars,
    topProductBars,
    quickInsights,
    alerts,
    comparisonCards,
  };
}
