import type { AnalysisCustomerRank, AnalysisProductRank } from "../../types/bi.types";
import { Card, CardTitle } from "../../../../components/ui/Card";
import { ContributionPieChart } from "../charts/ContributionPieChart";
import { formatCompactMoney, formatNumber, shortenLabel } from "../../../../utils/formatters";

export function TopContributeursSection({
  topProducts,
  topCustomers,
}: {
  topProducts: AnalysisProductRank[];
  topCustomers: AnalysisCustomerRank[];
}) {
  return (
    <section className="dashboard-section">
      <div className="dashboard-section-head">
        <div>
          <span>Top contributeurs</span>
          <h2>Produits et clients</h2>
        </div>
        <p>Ces classements suivent les mêmes filtres de période, commercial et type d'article.</p>
      </div>
      <div className="dashboard-grid bi-dashboard-grid analysis-contribution-grid">
        <Card className="analysis-chart-card">
          <CardTitle title="Top produits" subtitle="Produits par CA facturé." />
          <ContributionPieChart
            data={topProducts.map((item) => ({
              name: shortenLabel(item.product_name, 24),
              fullName: item.product_name,
              value: item.invoiced_sales,
              meta: `${formatNumber(item.quantity)} unités · ${item.category_name}${item.distributor_name ? ` · ${item.distributor_name}` : item.supplier_name ? ` · ${item.supplier_name}` : ""}`,
            }))}
            emptyText="Aucun produit à afficher pour ces filtres."
          />
        </Card>
        <Card className="analysis-chart-card">
          <CardTitle title="Top clients" subtitle="Clients par CA facturé." />
          <ContributionPieChart
            data={topCustomers.map((item) => ({
              name: shortenLabel(item.customer_name, 24),
              fullName: item.customer_name,
              value: item.invoiced_sales,
              meta: `${formatNumber(item.invoice_count)} factures · moy. ${formatCompactMoney(item.average_invoice_value)}`,
            }))}
            emptyText="Aucun client à afficher pour ces filtres."
          />
        </Card>
      </div>
    </section>
  );
}
