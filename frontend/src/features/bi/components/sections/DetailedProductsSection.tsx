import type { AnalysisProductRank } from "../../types/bi.types";
import { Card, CardTitle } from "../../../../components/ui/Card";
import { DataTable } from "../../../../components/ui/DataTable";
import { formatMoneyFull, formatNumber } from "../../../../utils/formatters";

export function DetailedProductsSection({ topProducts }: { topProducts: AnalysisProductRank[] }) {
  return (
    <section className="dashboard-section">
      <Card>
        <CardTitle title="Données détaillées" subtitle="Contribution produit, catégorie et fournisseur sur la période filtrée." />
        <DataTable
          rows={topProducts.slice(0, 10).map((item) => ({
            Produit: item.product_name,
            "Type d'article": item.category_name,
            Fournisseur: item.supplier_name || "Sans fournisseur",
            Distributeur: item.distributor_name || "Sans distributeur",
            Quantite: formatNumber(item.quantity),
            Lignes: item.line_count,
            "CA facture": formatMoneyFull(item.invoiced_sales),
          }))}
        />
      </Card>
    </section>
  );
}
