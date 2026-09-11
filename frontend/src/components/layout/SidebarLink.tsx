import { NavLink } from "react-router-dom";
import type { SidebarNavItem } from "../../constants/navigation";

export function SidebarLink({ item }: { item: SidebarNavItem }) {
  const Icon = item.icon;
  return (
    <NavLink to={item.to} className="sb-nav-item">
      <Icon size={20} strokeWidth={2.45} />
      <span>{item.label}</span>
    </NavLink>
  );
}
