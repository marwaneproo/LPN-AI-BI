import { CheckCircle2, Coffee, Moon, Sun } from "lucide-react";
import type { ReactNode } from "react";
import type { Theme } from "../../types/common.types";

export function ThemeOption({
  active,
  title,
  body,
  icon,
  swatches,
  onClick,
}: {
  active: boolean;
  title: string;
  body: string;
  icon: ReactNode;
  swatches: string[];
  onClick: () => void;
}) {
  return (
    <button className={active ? "theme-option active-theme-option" : "theme-option"} onClick={onClick}>
      <span className="theme-option-head">
        {icon}
        <strong>{title}</strong>
        {active ? <CheckCircle2 size={15} /> : null}
      </span>
      <span>{body}</span>
      <span className="swatches">
        {swatches.map((swatch) => (
          <i key={swatch} style={{ background: swatch }} />
        ))}
      </span>
    </button>
  );
}

export function ThemeBadge({ theme }: { theme: Theme }) {
  const content = {
    dark: { label: "Sombre", icon: <Moon size={13} /> },
    light: { label: "Claire", icon: <Sun size={13} /> },
    cream: { label: "Cream Coffee", icon: <Coffee size={13} /> },
  }[theme];
  return (
    <span className="theme-badge">
      {content.icon}
      {content.label}
    </span>
  );
}
