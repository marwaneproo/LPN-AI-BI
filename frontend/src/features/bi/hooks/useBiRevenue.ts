import { useEffect, useMemo, useState } from "react";
import { fetchBiRevenue } from "../api/biApi";
import type { BiRevenueFilters, BiRevenueResponse, FormattedAnalysisTrendPoint, RankedBarDatum } from "../types/bi.types";
import { formatAnalysisPeriodLabel, formatPeriodLabel } from "../utils/biFormatters";
import { formatCompactMoney, formatMoneyFull, formatNumber, formatPercent, shortenLabel } from "../../../utils/formatters";

const TREND_COLORS = ["var(--chart-green)", "var(--chart-blue)", "var(--chart-amber)", "var(--bi-pink)", "var(--chart-violet)"];

export function useBiRevenue(filters: BiRevenueFilters = {}) {
  const [snapshot, setSnapshot] = useState<BiRevenueResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  async function refreshRevenue() {
    setIsLoading(true);
    setError("");
    try {
      setSnapshot(await fetchBiRevenue(filters));
    } catch (error) {
      setError(error instanceof Error ? error.message : "Chiffre d'affaires BI indisponible.");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void refreshRevenue();
  }, [filters.compare, filters.from, filters.granularity, filters.to, filters.commercial]);

  const kpis = snapshot?.data.kpis;
  const yearlyPoints = useMemo(() => snapshot?.data.yearly ?? [], [snapshot]);
  const trendItems = useMemo(
    () =>
      (snapshot?.data.trend ?? []).slice(-6).map((point, index) => ({
        label: formatPeriodLabel(point.period),
        value: Number(point.invoiced_sales ?? 0),
        color: TREND_COLORS[index % TREND_COLORS.length],
      })),
    [snapshot],
  );
  const responsibleSignals = useMemo(() => {
    const reading = snapshot?.data.responsible_reading;
    return [
      {
        label: "Meilleur mois",
        value: reading?.best_month?.period ? formatPeriodLabel(reading.best_month.period) : "Indisponible",
        helper: reading?.best_month ? `CA facturé: ${formatCompactMoney(reading.best_month.invoiced_sales)}` : "Aucun mois disponible",
      },
      {
        label: "Couverture",
        value: formatPercent(reading?.invoice_coverage_percent ?? 0),
        helper: "CA facturé / CA commandé",
      },
      {
        label: "Risque",
        value: `${formatNumber(reading?.unpaid_invoice_count ?? 0)} impayées`,
        helper: `Montant impayé: ${formatCompactMoney(reading?.unpaid_invoice_amount ?? 0)}`,
      },
    ];
  }, [snapshot]);
  const revenueStatusSignals = useMemo(() => {
    const status = snapshot?.data.revenue_status;
    return [
      {
        label: "Facturé",
        value: formatCompactMoney(status?.facture ?? 0),
        helper: "CA facturé depuis mart_sales_daily",
      },
      {
        label: "A livrer",
        value: formatNumber(status?.a_livrer ?? 0),
        helper: "Quantité restante depuis mart_order_to_invoice_flow",
      },
      {
        label: "Ecart",
        value: formatCompactMoney(status?.ecart ?? 0),
        helper: "CA facturé moins CA commandé",
      },
    ];
  }, [snapshot]);

  const comparisonCards = useMemo(() => {
    const years = [...(snapshot?.data.comparison ?? [])].sort((a, b) => a.year - b.year);
    if (!years.length) return [];
    const current = years[years.length - 1];
    const previous = years.length > 1 ? years[years.length - 2] : null;
    const prevYear = previous?.year ?? current.year;
    const basket = (point: typeof current | null) =>
      point && point.order_count > 0 ? Number(point.ca_commande || 0) / point.order_count : 0;
    // Cumulative CA stays an all-years reading, so it comes from `yearly`, not the window.
    const allYears = [...(snapshot?.data.yearly ?? [])].sort((a, b) => a.year - b.year);
    const cumulativeCurrent = allYears.reduce((sum, y) => sum + Number(y.ca_facture || 0), 0);
    const cumulativePrevious = allYears
      .filter((y) => y.year < (allYears.length ? allYears[allYears.length - 1].year : 0))
      .reduce((sum, y) => sum + Number(y.ca_facture || 0), 0);

    return [
      {
        key: "reel",
        label: "CA réel",
        tone: "green" as const,
        value: formatCompactMoney(current.ca_facture),
        helper: `Facturé ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.ca_facture || 0),
        previousValue: Number(previous?.ca_facture ?? 0),
      },
      {
        key: "commande",
        label: "CA commandé",
        tone: "blue" as const,
        value: formatCompactMoney(current.ca_commande),
        helper: `Commandé ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.ca_commande || 0),
        previousValue: Number(previous?.ca_commande ?? 0),
      },
      {
        key: "ecart",
        label: "Écart facturation",
        tone: "pink" as const,
        value: formatCompactMoney(current.ecart),
        helper: `Facturé − commandé ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.ecart || 0),
        previousValue: Number(previous?.ecart ?? 0),
        increaseIsGood: false,
      },
      {
        key: "panier",
        label: "Panier moyen",
        tone: "amber" as const,
        value: formatCompactMoney(basket(current)),
        helper: `Par commande ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: basket(current),
        previousValue: basket(previous),
      },
      {
        key: "cumul",
        label: "CA cumulatif",
        tone: "violet" as const,
        value: formatCompactMoney(cumulativeCurrent),
        helper: "Cumul facturé toutes années",
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: cumulativeCurrent,
        previousValue: cumulativePrevious,
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
        orderedSales: Number(point.ordered_sales ?? 0),
        invoiceCount: Number(point.invoice_count ?? 0),
        invoicedSales: Number(point.invoiced_sales ?? 0),
        ...(previous
          ? {
              prevOrderedSales: Number(previous.ordered_sales ?? 0),
              prevInvoicedSales: Number(previous.invoiced_sales ?? 0),
            }
          : {}),
      };
    });
  }, [snapshot]);

  const unpaid = snapshot?.data.unpaid;
  const unpaidAmountLabel = formatCompactMoney(unpaid?.unpaid_amount ?? 0);
  const unpaidRateLabel = formatPercent(unpaid?.unpaid_rate_percent ?? 0);

  const unpaidCustomerRows = useMemo(
    () =>
      (unpaid?.top_customers ?? []).map((customer, index) => ({
        id: `unpaid-customer-${index}`,
        name: shortenLabel(customer.customer_name, 32),
        amount: formatMoneyFull(customer.amount),
        invoiceCount: customer.unpaid_invoice_count,
      })),
    [unpaid],
  );

  const unpaidCommercialBars = useMemo<RankedBarDatum[]>(
    () =>
      (unpaid?.top_commercials ?? []).map((commercial, index) => ({
        // Real salesrep_id as the bar id so the page's click-to-filter can toggle on it.
        id: commercial.salesrep_id ? String(commercial.salesrep_id) : `unpaid-commercial-${index}`,
        name: shortenLabel(commercial.commercial_name, 20),
        fullName: commercial.commercial_name,
        value: Number(commercial.amount ?? 0),
        meta: `${formatNumber(commercial.unpaid_invoice_count)} facture(s) impayée(s)`,
        selected: filters.commercial !== undefined && commercial.salesrep_id === filters.commercial,
      })),
    [unpaid, filters.commercial],
  );

  const revenueStatusInsights = useMemo(() => {
    const status = snapshot?.data.revenue_status;
    const reading = snapshot?.data.responsible_reading;
    return [
      { label: "CA facturé", title: "Factures validées", value: formatCompactMoney(status?.facture ?? 0), accent: "var(--chart-green)" },
      { label: "Reste à livrer", title: "Quantité en attente", value: formatNumber(status?.a_livrer ?? 0), accent: "var(--chart-amber)" },
      { label: "Écart facturation", title: "Facturé − commandé", value: formatCompactMoney(status?.ecart ?? 0), accent: "var(--bi-pink)" },
      {
        label: "Impayés",
        title: `${formatNumber(reading?.unpaid_invoice_count ?? 0)} factures`,
        value: formatCompactMoney(reading?.unpaid_invoice_amount ?? 0),
        accent: "var(--chart-blue)",
      },
    ];
  }, [snapshot]);

  return {
    snapshot,
    isLoading,
    error,
    refreshRevenue,
    kpis,
    yearlyPoints,
    trendItems,
    responsibleSignals,
    revenueStatusSignals,
    trendPoints,
    revenueStatusInsights,
    comparisonCards,
    unpaidAmountLabel,
    unpaidRateLabel,
    unpaidCustomerRows,
    unpaidCommercialBars,
  };
}
