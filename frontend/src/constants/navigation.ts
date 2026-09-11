import type { LucideIcon } from "lucide-react";
import { BarChart3, CalendarClock, History, LineChart, MessageCircle, Settings, ShieldCheck } from "lucide-react";
import type { AppRole } from "../features/auth/types/auth.types";

export type SidebarNavItem = {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Roles allowed to see/use this item. Omitted = visible to every authenticated role. */
  roles?: AppRole[];
};

export const mainNavItems: SidebarNavItem[] = [
  { to: "/conversation", label: "Conversation", icon: MessageCircle },
  { to: "/tableau-de-bord", label: "BI", icon: BarChart3, roles: ["ADMIN", "DG", "ACHATS", "COMMERCIAL", "LOGISTIQUE", "DAF", "USER"] },
  { to: "/previsions", label: "Prévisions", icon: LineChart, roles: ["ADMIN", "DG", "ACHATS", "COMMERCIAL", "LOGISTIQUE", "DAF", "USER"] },
  { to: "/planification-ia", label: "Planification IA", icon: CalendarClock, roles: ["ADMIN", "DG", "ACHATS", "COMMERCIAL", "LOGISTIQUE", "DAF", "USER"] },
];

export function navItemsForRole(items: SidebarNavItem[], role: AppRole): SidebarNavItem[] {
  return items.filter((item) => !item.roles || item.roles.includes(role));
}

export const historiqueSubItems = [
  { to: "/historique", label: "Toutes les sessions" },
  { to: "/historique?filter=recent", label: "Récentes" },
];

export const historyNavItem: SidebarNavItem = { to: "/historique", label: "Historique", icon: History };
export const adminNavItem: SidebarNavItem = { to: "/admin", label: "Admin", icon: ShieldCheck };
export const settingsNavItem: SidebarNavItem = { to: "/reglages", label: "Réglages", icon: Settings };

/* Legacy flat list kept for breadcrumb title resolution */
export const navItems = [
  ...mainNavItems,
  historyNavItem,
  adminNavItem,
  settingsNavItem,
];
