import { BarChart3, Boxes, ClipboardList, LayoutDashboard, TrendingUp, Truck, UsersRound } from "lucide-react";
import { NavLink } from "react-router-dom";

const biSections = [
  {
    to: "/tableau-de-bord/vue-ensemble",
    label: "Vue d'ensemble",
    icon: LayoutDashboard,
  },
  {
    to: "/tableau-de-bord/commandes",
    label: "Commande",
    icon: ClipboardList,
  },
  {
    to: "/tableau-de-bord/chiffre-affaires",
    label: "Chiffre d'affaires",
    icon: TrendingUp,
  },
  {
    to: "/tableau-de-bord/articles",
    label: "Articles",
    icon: Boxes,
  },
  {
    to: "/tableau-de-bord/clients",
    label: "Client",
    icon: UsersRound,
  },
  {
    to: "/tableau-de-bord/achats",
    label: "Achats",
    icon: Truck,
  },
];

export function BiSectionNav() {
  return (
    <nav className="bi-section-nav" aria-label="Navigation BI">
      <span className="bi-section-nav-marker" aria-hidden="true">
        <BarChart3 size={17} />
      </span>
      {biSections.map((section) => {
        const Icon = section.icon;
        return (
          <NavLink
            key={section.to}
            to={section.to}
            className={({ isActive }) => `bi-section-link${isActive ? " bi-section-link-active" : ""}`}
          >
            <Icon size={17} strokeWidth={2.35} />
            <span>{section.label}</span>
          </NavLink>
        );
      })}
    </nav>
  );
}
