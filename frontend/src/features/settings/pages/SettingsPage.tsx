import { Coffee, Moon, Sun } from "lucide-react";
import { Card, CardTitle } from "../../../components/ui/Card";
import { ThemeOption } from "../../../components/ui/ThemeBadge";
import { PageFrame } from "../../../components/layout/PageFrame";
import type { Theme, Density } from "../../../types/common.types";

export function SettingsPage({
  theme,
  density,
  setTheme,
  setDensity,
}: {
  theme: Theme;
  density: Density;
  setTheme: (theme: Theme) => void;
  setDensity: (density: Density) => void;
}) {
  return (
    <PageFrame title="Réglages" subtitle="Personnalisation locale du frontend autonome.">
      <Card>
        <CardTitle title="Apparence" subtitle="Choisir la teinte de travail." />
        <div className="theme-grid">
          <ThemeOption
            active={theme === "dark"}
            title="Sombre"
            body="Neutre, froid, très contrasté."
            icon={<Moon size={17} />}
            swatches={["#14151a", "#25272b", "#5a86d3"]}
            onClick={() => setTheme("dark")}
          />
          <ThemeOption
            active={theme === "light"}
            title="Claire"
            body="Lisible en environnement lumineux."
            icon={<Sun size={17} />}
            swatches={["#f7f8f9", "#ffffff", "#4a76c5"]}
            onClick={() => setTheme("light")}
          />
          <ThemeOption
            active={theme === "cream"}
            title="Cream Coffee"
            body="Café noir mélangé au lait: brun crème, doux et chaleureux."
            icon={<Coffee size={17} />}
            swatches={["#1f1711", "#3a2a1e", "#d2a46f"]}
            onClick={() => setTheme("cream")}
          />
        </div>
      </Card>

      <Card>
        <CardTitle title="Densité" subtitle="Adapter l'interface à ton rythme de travail." />
        <div className="segmented-row">
          <button className={density === "comfortable" ? "segment active-segment" : "segment"} onClick={() => setDensity("comfortable")}>
            Comfortable
          </button>
          <button className={density === "compact" ? "segment active-segment" : "segment"} onClick={() => setDensity("compact")}>
            Compact
          </button>
        </div>
      </Card>
    </PageFrame>
  );
}
