import { useEffect, useMemo, useState } from "react";
import { fetchBiAchats } from "../api/biApi";
import type { BiAchatsFilters, BiAchatsResponse, RankedBarDatum } from "../types/bi.types";
import type { ParetoDatum } from "../components/charts/ParetoChart";
import { formatCompactMoney, formatNumber, shortenLabel } from "../../../utils/formatters";

const SUPPLIER_COLORS = ["var(--chart-blue)", "var(--chart-green)", "var(--bi-pink)", "var(--chart-amber)", "var(--chart-violet)", "var(--chart-blue-muted)"];

export function useBiAchats(filters: BiAchatsFilters = {}) {
  const [snapshot, setSnapshot] = useState<BiAchatsResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  async function refreshAchats() {
    setIsLoading(true);
    setError("");
    try {
      setSnapshot(await fetchBiAchats(filters));
    } catch (error) {
      setError(error instanceof Error ? error.message : "Achats BI indisponible.");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void refreshAchats();
  }, [filters.category, filters.compare, filters.from, filters.granularity, filters.supplier, filters.to]);

  const kpis = snapshot?.data.kpis;
  const catalog = snapshot?.data.catalog;

  const topSupplierBars = useMemo<RankedBarDatum[]>(
    () =>
      (snapshot?.data.top_suppliers ?? []).slice(0, 8).map((supplier, index) => ({
        id: `supplier-${index}`,
        name: shortenLabel(supplier.supplier_name, 24),
        fullName: supplier.supplier_name,
        value: Number(supplier.total_attributed_sales ?? 0),
        meta: `${formatNumber(supplier.product_count)} produits · ${formatCompactMoney(supplier.total_attributed_sales)}`,
      })),
    [snapshot],
  );

  const supplierParetoBars = useMemo<ParetoDatum[]>(() => {
    const suppliers = (snapshot?.data.top_suppliers ?? []).slice(0, 8);
    const total = suppliers.reduce((sum, supplier) => sum + Number(supplier.total_attributed_sales ?? 0), 0);
    let cumulative = 0;
    return suppliers.map((supplier, index) => {
      const value = Number(supplier.total_attributed_sales ?? 0);
      const part = total > 0 ? (value / total) * 100 : 0;
      cumulative += part;
      return {
        id: `supplier-pareto-${index}`,
        name: shortenLabel(supplier.supplier_name, 16),
        fullName: supplier.supplier_name,
        value,
        part,
        cumulative,
      };
    });
  }, [snapshot]);

  const topSupplierProductBars = useMemo<RankedBarDatum[]>(
    () =>
      (snapshot?.data.top_supplier_products ?? []).slice(0, 8).map((row, index) => ({
        id: `supplier-product-${index}`,
        name: shortenLabel(row.product_name, 22),
        fullName: `${row.product_name} · ${row.supplier_name}`,
        value: Number(row.total_attributed_sales ?? 0),
        meta: `${shortenLabel(row.supplier_name, 20)} · ${formatCompactMoney(row.total_attributed_sales)}`,
      })),
    [snapshot],
  );

  const categoryBars = useMemo<RankedBarDatum[]>(
    () =>
      (snapshot?.data.by_category ?? []).slice(0, 8).map((row, index) => ({
        id: `category-${index}`,
        name: shortenLabel(row.category_name, 20),
        fullName: row.category_name,
        value: Number(row.total_attributed_sales ?? 0),
        meta: `${formatNumber(row.supplier_count)} fournisseurs`,
        color: SUPPLIER_COLORS[index % SUPPLIER_COLORS.length],
      })),
    [snapshot],
  );

  const categoryQuantityBars = useMemo<RankedBarDatum[]>(
    () =>
      (snapshot?.data.by_category ?? [])
        .slice()
        .sort((a, b) => Number(b.total_quantity ?? 0) - Number(a.total_quantity ?? 0))
        .slice(0, 8)
        .map((row, index) => ({
          id: `category-quantity-${index}`,
          name: shortenLabel(row.category_name, 20),
          fullName: row.category_name,
          value: Number(row.total_quantity ?? 0),
          meta: `${formatNumber(row.supplier_count)} fournisseurs · ${formatCompactMoney(row.total_attributed_sales)}`,
        })),
    [snapshot],
  );

  const trendPoints = useMemo(
    () =>
      (snapshot?.data.trend ?? []).map((point) => ({
        period: point.period,
        supplierCount: point.supplier_count,
        totalAttributedSales: Number(point.total_attributed_sales ?? 0),
      })),
    [snapshot],
  );

  const trendBars = useMemo<RankedBarDatum[]>(
    () =>
      (snapshot?.data.trend ?? []).map((point, index) => ({
        id: `trend-${index}`,
        name: point.period,
        fullName: point.period,
        value: Number(point.total_attributed_sales ?? 0),
        meta: `${formatNumber(point.supplier_count)} fournisseurs actifs`,
      })),
    [snapshot],
  );

  return {
    snapshot,
    isLoading,
    error,
    refreshAchats,
    kpis,
    catalog,
    methodologyNote: snapshot?.data.methodology_note ?? "",
    topSupplierBars,
    supplierParetoBars,
    topSupplierProductBars,
    categoryBars,
    categoryQuantityBars,
    trendPoints,
    trendBars,
  };
}
