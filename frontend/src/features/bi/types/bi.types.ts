export type BiGranularity = "day" | "week" | "month";

export type BiOverviewFilters = {
  from?: string;
  to?: string;
  granularity?: BiGranularity;
  compare?: boolean;
};

export type BiRevenueFilters = BiOverviewFilters & {
  /** salesrep_id — every revenue reading (KPIs, trend, statuts, impayés, yearly) honors it backend-side. */
  commercial?: number;
};
export type BiOrdersFilters = BiOverviewFilters & {
  /** salesrep_id — every orders reading (KPIs, types, funnel, statuts, lecture commerciale) honors it backend-side. */
  commercial?: number;
};
export type BiArticlesFilters = BiOverviewFilters;
export type BiAchatsFilters = BiOverviewFilters & {
  supplier?: number;
  category?: number;
};
export type BiClientsFilters = BiOverviewFilters & {
  commercial?: number | string;
  customer?: number | string;
  category?: number | string;
  supplier?: number | string;
  region?: string;
  city?: string;
  payment_status?: string;
  limit?: number;
  offset?: number;
};

export type BiOverviewResponse = {
  meta: BiResponseMeta;
  data: BiOverviewData;
};

export type BiRevenueResponse = {
  meta: BiResponseMeta;
  data: BiRevenueData;
};

export type BiOrdersResponse = {
  meta: BiResponseMeta;
  data: BiOrdersData;
};

export type BiArticlesResponse = {
  meta: BiResponseMeta;
  data: BiArticlesData;
};

export type BiClientsResponse = {
  meta: BiResponseMeta;
  data: BiClientsData;
};

export type BiResponseMeta = {
  request_id: string;
  generated_at: string;
  latency_ms: number;
  applied_filters: Record<string, unknown>;
  pagination: BiPagination;
};

export type BiPagination = {
  limit: number | null;
  offset: number | null;
  returned: number | null;
  has_more: boolean;
};

export type BiOverviewData = {
  kpis: SalesKpis;
  trend: MonthlySalesPoint[];
  /** Same window one year back, re-labelled with the current window's periods. Empty unless `compare` is on. */
  compare_trend: MonthlySalesPoint[];
  quick_signals: BiOverviewQuickSignals;
  sales_mix: BiOverviewSalesMix;
  top_customers: CustomerRank[];
  top_products: ProductRank[];
  order_statuses: StatusBreakdown[];
  invoice_statuses: StatusBreakdown[];
  yearly: BiOverviewYearlyPoint[];
};

export type BiOverviewYearlyPoint = {
  year: number;
  invoiced_sales: number;
  order_value: number;
  order_count: number;
  invoice_count: number;
  active_customers: number;
  invoice_coverage_percent: number;
};

export type BiOverviewQuickSignals = {
  top_commercial: BiNamedMetric | null;
  top_supplier: BiNamedMetric | null;
  top_region: BiNamedMetric | null;
};

export type BiNamedMetric = {
  name: string;
  value: number;
};

export type BiOverviewSalesMix = {
  order_value: number;
  invoiced_sales: number;
  tracked_products: number;
};

export type BiRevenueData = {
  kpis: BiRevenueKpis;
  trend: BiRevenueTrendPoint[];
  /** Same window one year back, re-labelled with the current window's periods. Empty unless `compare` is on. */
  compare_trend: BiRevenueTrendPoint[];
  yearly: BiRevenueYearlyPoint[];
  /** Selected window vs same window one year back — feeds the YoY comparison cards. */
  comparison: BiRevenueYearlyPoint[];
  responsible_reading: BiRevenueResponsibleReading;
  revenue_status: BiRevenueStatus;
  unpaid: BiUnpaidExposure;
};

/** Unpaid-invoice exposure over the selected period, from mart_payment_status. */
export type BiUnpaidExposure = {
  unpaid_amount: number;
  unpaid_rate_percent: number;
  top_customers: BiUnpaidCustomer[];
  top_commercials: BiUnpaidCommercial[];
};

export type BiUnpaidCustomer = {
  customer_name: string;
  amount: number;
  unpaid_invoice_count: number;
};

export type BiUnpaidCommercial = {
  commercial_name: string;
  amount: number;
  unpaid_invoice_count: number;
  /** commercial_key of the mart — sent back as the `commercial` click-filter param. */
  salesrep_id: number;
};

export type BiRevenueYearlyPoint = {
  year: number;
  ca_facture: number;
  ca_commande: number;
  ecart: number;
  order_count: number;
  invoice_count: number;
  partial: boolean;
};

export type BiRevenueKpis = {
  invoiced_sales: number;
  ordered_sales: number;
  invoice_gap_amount: number;
  average_order_value: number;
};

export type BiRevenueTrendPoint = {
  period: string;
  ordered_sales: number;
  invoiced_sales: number;
  order_count: number;
  invoice_count: number;
};

export type BiRevenueResponsibleReading = {
  best_month: BiRevenueBestMonth;
  invoice_coverage_percent: number;
  unpaid_invoice_count: number;
  unpaid_invoice_amount: number;
};

export type BiRevenueBestMonth = {
  period: string;
  invoiced_sales: number;
};

export type BiRevenueStatus = {
  facture: number;
  a_livrer: number;
  ecart: number;
};

export type BiOrdersData = {
  kpis: BiOrdersKpis;
  by_type: BiOrderTypeRank[];
  commercial_flow: BiCommercialOrderFlow[];
  status_breakdown: BiOrderStatusBreakdown[];
  funnel: BiOrderFunnel;
  yearly: BiOrdersYearlyPoint[];
};

/** Ordered → delivered → invoiced conversion funnel; coverage percents are relative to the ordered quantity. */
export type BiOrderFunnel = {
  quantity_ordered: number;
  quantity_delivered: number;
  quantity_invoiced: number;
  delivery_coverage_percent: number;
  invoice_coverage_percent: number;
};

export type BiOrdersYearlyPoint = {
  year: number;
  order_value: number;
  order_count: number;
  invoice_coverage_percent: number;
  delivery_coverage_percent: number;
};

export type BiOrdersKpis = {
  order_count: number;
  dominant_type_name: string;
  invoice_coverage_percent: number;
  amount_invoice_coverage_percent: number;
  status_count: number;
};

export type BiOrderTypeRank = {
  order_type_id: number;
  order_type_name: string;
  order_count: number;
  ordered_sales: number;
};

export type BiCommercialOrderFlow = {
  salesrep_id: number;
  commercial_label: string;
  order_count: number;
  ordered_sales: number;
  delivered_coverage_percent: number;
  invoice_coverage_percent: number;
};

export type BiOrderStatusBreakdown = {
  status: string;
  label: string;
  item_count: number;
  total_value: number;
};

export type BiArticlesData = {
  kpis: BiArticlesKpis;
  concentration: BiArticleConcentration;
  top_articles: BiArticleRank[];
  mix_article: BiArticleMix;
  stock_priorities: BiStockRiskRank[];
  yearly: BiArticlesYearlyPoint[];
};

/** Revenue-concentration (Pareto) summary: share of CA held by the 10 largest products. */
export type BiArticleConcentration = {
  top10_share_percent: number;
  total_products: number;
};

export type BiArticlesYearlyPoint = {
  year: number;
  invoiced_sales: number;
  active_products: number;
  quantity: number;
  active_categories: number;
  active_themes: number;
};

export type BiArticlesKpis = {
  active_products: number;
  top_category_name: string;
  top_theme_name: string;
  stock_risk_count: number;
};

export type BiArticleRank = {
  product_id: number;
  product_name: string;
  category_name: string;
  supplier_id: number;
  supplier_name: string;
  line_count: number;
  quantity: number;
  invoiced_sales: number;
  cumulative_share_percent: number;
};

export type BiArticleMix = {
  categories: BiNamedValue[];
  themes: BiNamedValue[];
  collections: BiNamedValue[];
};

export type BiNamedValue = {
  name: string;
  value: number;
};

export type BiStockRiskRank = {
  product_id: number;
  product_name: string;
  category_name: string;
  theme_name: string;
  supplier_name: string;
  qty_on_hand: number;
  qty_reserved: number;
  qty_available: number;
  qty_ordered: number;
  is_stockout_risk: boolean;
  stock_risk_level: string;
};

export type BiClientsData = {
  kpis: BiClientsKpis;
  concentration: BiClientConcentration;
  top_clients: BiClientRank[];
  by_commercial: BiClientCommercialRank[];
  by_article: BiClientArticleRank[];
  by_region: BiClientRegionRank[];
  finance_risks: BiClientFinanceRisk[];
  yearly: BiClientsYearlyPoint[];
};

/** Revenue-concentration (Pareto) summary: share of CA held by the 10 largest customers. */
export type BiClientConcentration = {
  top10_share_percent: number;
  total_customers: number;
};

export type BiClientsYearlyPoint = {
  year: number;
  invoiced_sales: number;
  active_customers: number;
  active_commercials: number;
  active_regions: number;
};

export type BiClientsKpis = {
  active_customers: number;
  top_customer_name: string;
  portfolio_commercial_count: number;
  geography_count: number;
};

export type BiClientRank = {
  customer_id: number;
  customer_name: string;
  invoice_count: number;
  invoiced_sales: number;
  average_invoice_value: number;
  cumulative_share_percent: number;
};

export type BiClientCommercialRank = {
  salesrep_id: number;
  commercial_label: string;
  customer_count: number;
  invoiced_sales: number;
};

export type BiClientArticleRank = {
  customer_id: number;
  customer_name: string;
  product_name: string;
  quantity: number;
  invoiced_sales: number;
};

export type BiClientRegionRank = {
  region_name: string;
  city_name: string;
  customer_count: number;
  invoice_count: number;
  invoiced_sales: number;
};

export type BiClientFinanceRisk = {
  customer_id: number;
  customer_name: string;
  unpaid_invoice_count: number;
  unpaid_invoice_amount: number;
};

export type SalesKpis = {
  order_count: number;
  order_value: number;
  active_customers: number;
  invoice_count: number;
  invoiced_sales: number;
  paid_invoice_count: number;
  unpaid_invoice_count: number;
  invoice_coverage_percent: number;
};

export type MonthlySalesPoint = {
  period: string;
  order_count: number;
  order_value: number;
  invoice_count: number;
  invoiced_sales: number;
};

export type FormattedTrendPoint = {
  label: string;
  period: string;
  orderCount: number;
  orderValue: number;
  invoiceCount: number;
  invoicedSales: number;
};

export type FormattedAnalysisTrendPoint = {
  label: string;
  period: string;
  orderCount: number;
  orderedSales: number;
  invoiceCount: number;
  invoicedSales: number;
  /** Previous-period values, present only when the "Comparer" toggle is on. */
  prevOrderedSales?: number;
  prevInvoicedSales?: number;
};

export type CustomerRank = {
  customer_name: string;
  order_count: number;
  total_order_value: number;
  average_order_value: number;
};

export type ProductRank = {
  product_name: string;
  line_count: number;
  total_quantity: number;
  total_order_value: number;
};

export type StatusBreakdown = {
  status: string;
  label: string;
  item_count: number;
  total_value: number;
};

export type RankedBarDatum = {
  id?: string;
  name: string;
  fullName: string;
  value: number;
  meta: string;
  selected?: boolean;
};

export type ContributionPieDatum = RankedBarDatum & {
  color: string;
  percent: number;
};

export type AnalysisProductRank = {
  product_name: string;
  category_name: string;
  supplier_id?: number | null;
  supplier_name?: string | null;
  distributor_id?: number | null;
  distributor_name?: string | null;
  line_count: number;
  quantity: number;
  invoiced_sales: number;
};

export type AnalysisCustomerRank = {
  customer_name: string;
  invoice_count: number;
  invoiced_sales: number;
  average_invoice_value: number;
};

export type FilterOption = {
  value: string;
  label: string;
  item_count: number;
  total_value: number;
};

/** How a BiDataTable cell renders a raw row value. "text" (default) prints it as-is. */
export type BiDetailColumnFormat = "money" | "moneyCompact" | "percent" | "number" | "text";

export type BiDetailColumn = {
  key: string;
  label: string;
  format?: BiDetailColumnFormat;
};

/** Booleans are allowed so raw DTO rows (e.g. stock flags) type-check; only keys listed in `columns` are ever rendered. */
export type BiDetailRow = Record<string, string | number | boolean | null | undefined>;

/** Feeds BiWidgetCard's optional "Voir les données" drawer: the raw rows behind one chart. */
export type BiWidgetDetail = {
  title: string;
  columns: BiDetailColumn[];
  rows: BiDetailRow[];
  filename: string;
};

export type BiAchatsResponse = {
  meta: BiResponseMeta;
  data: BiAchatsData;
};

export type BiAchatsData = {
  kpis: BiAchatsKpis;
  trend: BiAchatsTrendPoint[];
  top_suppliers: BiAchatsSupplierRank[];
  top_supplier_products: BiAchatsSupplierProductRank[];
  by_category: BiAchatsCategoryBreakdown[];
  catalog: BiAchatsCatalogSnapshot;
  methodology_note: string;
};

export type BiAchatsKpis = {
  supplier_count: number;
  product_count: number;
  total_attributed_sales: number;
  average_sales_per_supplier: number;
};

export type BiAchatsTrendPoint = {
  period: string;
  supplier_count: number;
  total_attributed_sales: number;
};

export type BiAchatsSupplierRank = {
  supplier_name: string;
  product_count: number;
  total_attributed_sales: number;
  total_quantity: number;
};

export type BiAchatsSupplierProductRank = {
  supplier_name: string;
  product_name: string;
  total_attributed_sales: number;
  total_quantity: number;
};

export type BiAchatsCategoryBreakdown = {
  category_name: string;
  total_attributed_sales: number;
  supplier_count: number;
  total_quantity: number;
};

/** Point-in-time snapshot of business.m_product_po — not scoped to the page's date range. */
export type BiAchatsCatalogSnapshot = {
  catalog_entries: number;
  vendor_count: number;
  product_count: number;
  avg_catalog_price: number;
  avg_last_purchase_price: number;
  avg_last_invoiced_price: number;
};
