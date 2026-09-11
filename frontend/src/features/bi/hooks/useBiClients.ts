import { useEffect, useMemo, useState } from "react";
import { fetchBiClients } from "../api/biApi";
import type { BiClientsFilters, BiClientsResponse, RankedBarDatum } from "../types/bi.types";
import type { ParetoDatum } from "../components/charts/ParetoChart";
import { formatCompactMoney, formatNumber, formatPercent, shortenLabel } from "../../../utils/formatters";

const CLIENT_COLORS = ["var(--chart-blue)", "var(--chart-green)", "var(--bi-pink)", "var(--chart-amber)", "var(--chart-violet)", "var(--chart-blue-muted)"];

export function useBiClients(filters: BiClientsFilters = {}) {
  const [snapshot, setSnapshot] = useState<BiClientsResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  async function refreshClients() {
    setIsLoading(true);
    setError("");
    try {
      setSnapshot(await fetchBiClients(filters));
    } catch (error) {
      setError(error instanceof Error ? error.message : "Clients BI indisponibles.");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void refreshClients();
  }, [
    filters.category,
    filters.city,
    filters.commercial,
    filters.compare,
    filters.customer,
    filters.from,
    filters.granularity,
    filters.limit,
    filters.offset,
    filters.payment_status,
    filters.region,
    filters.supplier,
    filters.to,
  ]);

  const kpis = snapshot?.data.kpis;
  const topClientItems = useMemo(
    () =>
      (snapshot?.data.top_clients ?? []).slice(0, 6).map((client, index) => ({
        label: shortenLabel(client.customer_name, 18),
        value: Number(client.invoiced_sales ?? 0),
        color: CLIENT_COLORS[index % CLIENT_COLORS.length],
      })),
    [snapshot],
  );
  const clientAngleSignals = useMemo(() => {
    const topCommercial = snapshot?.data.by_commercial?.[0];
    const topArticle = snapshot?.data.by_article?.[0];
    const topRegion = snapshot?.data.by_region?.[0];
    return [
      topCommercial
        ? {
            label: "Commercial",
            value: shortenLabel(topCommercial.commercial_label, 18),
            helper: `${formatNumber(topCommercial.customer_count)} clients · ${formatCompactMoney(topCommercial.invoiced_sales)}`,
          }
        : null,
      topArticle
        ? {
            label: "Article",
            value: shortenLabel(topArticle.product_name, 18),
            helper: `${shortenLabel(topArticle.customer_name, 18)} · ${formatCompactMoney(topArticle.invoiced_sales)}`,
          }
        : null,
      topRegion
        ? {
            label: "Région",
            value: shortenLabel(topRegion.city_name || topRegion.region_name, 18),
            helper: `${formatNumber(topRegion.customer_count)} clients · ${formatCompactMoney(topRegion.invoiced_sales)}`,
          }
        : null,
    ].filter((item): item is { label: string; value: string; helper: string } => item !== null);
  }, [snapshot]);
  const financeRiskSignals = useMemo(
    () =>
      (snapshot?.data.finance_risks ?? []).slice(0, 3).map((risk) => ({
        label: shortenLabel(risk.customer_name, 22),
        value: formatCompactMoney(risk.unpaid_invoice_amount),
        helper: `${formatNumber(risk.unpaid_invoice_count)} facture(s) impayée(s)`,
      })),
    [snapshot],
  );

  const comparisonCards = useMemo(() => {
    const years = [...(snapshot?.data.yearly ?? [])].sort((a, b) => a.year - b.year);
    if (!years.length) return [];
    const current = years[years.length - 1];
    const previous = years.length > 1 ? years[years.length - 2] : null;
    const prevYear = previous?.year ?? current.year;
    const basket = (point: typeof current | null) =>
      point && point.active_customers > 0 ? Number(point.invoiced_sales || 0) / point.active_customers : 0;

    return [
      {
        key: "ca",
        label: "CA clients",
        tone: "green" as const,
        value: formatCompactMoney(current.invoiced_sales),
        helper: `Facturé clients ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.invoiced_sales || 0),
        previousValue: Number(previous?.invoiced_sales ?? 0),
      },
      {
        key: "clients",
        label: "Clients actifs",
        tone: "blue" as const,
        value: formatNumber(current.active_customers),
        helper: `Clients facturés ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.active_customers || 0),
        previousValue: Number(previous?.active_customers ?? 0),
      },
      {
        key: "portefeuille",
        label: "Portefeuille",
        tone: "amber" as const,
        value: formatNumber(current.active_commercials),
        helper: `Commerciaux contributeurs ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.active_commercials || 0),
        previousValue: Number(previous?.active_commercials ?? 0),
      },
      {
        key: "geographie",
        label: "Géographie",
        tone: "pink" as const,
        value: formatNumber(current.active_regions),
        helper: `Régions facturées ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.active_regions || 0),
        previousValue: Number(previous?.active_regions ?? 0),
      },
      {
        key: "panier",
        label: "Panier client",
        tone: "violet" as const,
        value: formatCompactMoney(basket(current)),
        helper: `CA moyen par client ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: basket(current),
        previousValue: basket(previous),
      },
    ];
  }, [snapshot]);

  const topClientBars = useMemo<RankedBarDatum[]>(
    () =>
      (snapshot?.data.top_clients ?? []).slice(0, 8).map((client, index) => ({
        id: `client-${index}`,
        name: shortenLabel(client.customer_name, 24),
        fullName: client.customer_name,
        value: Number(client.invoiced_sales ?? 0),
        meta: `${formatNumber(client.invoice_count)} factures · panier ${formatCompactMoney(client.average_invoice_value)}`,
      })),
    [snapshot],
  );

  const clientConcentration = snapshot?.data.concentration;
  const top10ShareLabel = clientConcentration ? formatPercent(Number(clientConcentration.top10_share_percent ?? 0)) : "—";

  const clientParetoBars = useMemo<ParetoDatum[]>(() => {
    const topClients = (snapshot?.data.top_clients ?? []).slice(0, 8);
    let previousCumulative = 0;
    return topClients.map((client, index) => {
      const cumulative = Number(client.cumulative_share_percent ?? 0);
      const part = cumulative - previousCumulative;
      previousCumulative = cumulative;
      return {
        id: `client-pareto-${index}`,
        name: shortenLabel(client.customer_name, 16),
        fullName: client.customer_name,
        value: Number(client.invoiced_sales ?? 0),
        part,
        cumulative,
      };
    });
  }, [snapshot]);

  const regionBars = useMemo<RankedBarDatum[]>(
    () =>
      (snapshot?.data.by_region ?? []).slice(0, 8).map((region, index) => {
        const label = region.city_name || region.region_name || "Zone inconnue";
        return {
          id: `region-${index}`,
          name: shortenLabel(label, 20),
          fullName: region.region_name ? `${label} · ${region.region_name}` : label,
          value: Number(region.invoiced_sales ?? 0),
          meta: `${formatNumber(region.customer_count)} clients · ${formatNumber(region.invoice_count)} factures`,
        };
      }),
    [snapshot],
  );

  const financeInsights = useMemo(
    () =>
      (snapshot?.data.finance_risks ?? []).slice(0, 6).map((risk) => ({
        label: `${formatNumber(risk.unpaid_invoice_count)} facture(s) impayée(s)`,
        title: shortenLabel(risk.customer_name, 30),
        value: formatCompactMoney(risk.unpaid_invoice_amount),
        helper: "Montant impayé",
        accent: "var(--danger, oklch(58% 0.2 25))",
      })),
    [snapshot],
  );

  return {
    snapshot,
    isLoading,
    error,
    refreshClients,
    kpis,
    topClientItems,
    clientAngleSignals,
    financeRiskSignals,
    topClientBars,
    clientParetoBars,
    top10ShareLabel,
    regionBars,
    financeInsights,
    comparisonCards,
  };
}
