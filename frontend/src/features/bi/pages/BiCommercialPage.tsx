import { BiShell } from "../components/layout/BiShell";
import { BiBarPreview, BiColumnPreview, BiMetricCard, BiSignalList, BiWidgetCard } from "../components/widgets/BiWidgets";
import { useBiRange } from "../hooks/useBiRange";

export function BiCommercialPage() {
  const { range, applyRange, compareEnabled, toggleCompare } = useBiRange();

  return (
    <BiShell
      title="Commercial"
      subtitle="Performance commerciale, CA réel et conversion commandes-factures."
      eyebrow="Equipes vente"
      description="Cette section concentrera la lecture par commercial: portefeuille, géographie, commandes créées et CA réellement facturé."
      range={range}
      onApplyRange={applyRange}
      compareEnabled={compareEnabled}
      onToggleCompare={toggleCompare}
    >
      <section className="bi-metric-grid" aria-label="Indicateurs commerciaux">
        <BiMetricCard label="Commerciaux" value="29" helper="Actifs dans les exports" tone="blue" />
        <BiMetricCard label="Top CA" value="afekkak" helper="Commercial leader" tone="green" />
        <BiMetricCard label="Conversion" value="à comparer" helper="Commandé vs facturé" tone="pink" />
        <BiMetricCard label="Couverture région" value="Ville/Région" helper="Analyse terrain" tone="amber" />
      </section>

      <section className="bi-widget-grid">
        <BiWidgetCard title="CA par commercial" subtitle="Comparaison visuelle prévue par vendeur." wide>
          <BiColumnPreview
            items={[
              { label: "afekkak", value: 92, color: "var(--chart-blue)" },
              { label: "midbymed", value: 58, color: "var(--chart-green)" },
              { label: "obelamri", value: 51, color: "var(--bi-pink)" },
              { label: "arifki", value: 39, color: "var(--chart-amber)" },
            ]}
          />
        </BiWidgetCard>

        <BiWidgetCard title="Conversion vente" subtitle="Commandes qui deviennent CA réel.">
          <BiBarPreview
            items={[
              { label: "Commandé", value: 88, color: "var(--chart-blue)" },
              { label: "Facturé", value: 72, color: "var(--chart-green)" },
              { label: "Reste", value: 19, color: "var(--chart-amber)" },
            ]}
          />
        </BiWidgetCard>

        <BiWidgetCard title="Lecture terrain" subtitle="Commercial, ville et région.">
          <BiSignalList
            items={[
              { label: "Zone forte", value: "Casa", helper: "CA concentré" },
              { label: "Client clé", value: "Top 10", helper: "Portefeuille" },
              { label: "Action", value: "forage", helper: "Clique commercial" },
            ]}
          />
        </BiWidgetCard>
      </section>
    </BiShell>
  );
}
