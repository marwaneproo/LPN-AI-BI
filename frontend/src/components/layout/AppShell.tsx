import { lazy, Suspense } from "react";
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Command,
  History,
  Home,
  LogOut,
  Moon,
  UserCircle,
} from "lucide-react";
import { NavLink, Navigate, Route, Routes, useLocation } from "react-router-dom";
import type { Theme, Density } from "../../types/common.types";
import { ROLE_LABELS, type AuthSession } from "../../features/auth/types/auth.types";
import {
  mainNavItems,
  historiqueSubItems,
  adminNavItem,
  settingsNavItem,
  navItems,
  navItemsForRole,
} from "../../constants/navigation";
import { useStoredState } from "../../hooks/useStoredState";
import { SidebarLink } from "./SidebarLink";
import { ThemeBadge } from "../ui/ThemeBadge";
import { FloatingAssistant } from "../chatbot/FloatingAssistant";
import robotIcon from "../../assets/brand/assistant-robot-icon.png";

const ConversationPage = lazy(() =>
  import("../../features/qa/pages/ConversationPage").then((m) => ({ default: m.ConversationPage }))
);
const ForecastsPage = lazy(() =>
  import("../../features/forecasts/pages/ForecastsPage").then((m) => ({ default: m.ForecastsPage }))
);
const PlanificationIaPage = lazy(() =>
  import("../../features/planning/pages/PlanificationIaPage").then((m) => ({ default: m.PlanificationIaPage }))
);
const HistoryPage = lazy(() =>
  import("../../features/history/pages/HistoryPage").then((m) => ({ default: m.HistoryPage }))
);
const AdminPage = lazy(() =>
  import("../../features/admin/pages/AdminPage").then((m) => ({ default: m.AdminPage }))
);
const SettingsPage = lazy(() =>
  import("../../features/settings/pages/SettingsPage").then((m) => ({ default: m.SettingsPage }))
);
const BiOverviewPage = lazy(() =>
  import("../../features/bi/pages/BiOverviewPage").then((m) => ({ default: m.BiOverviewPage }))
);
const BiCommandesPage = lazy(() =>
  import("../../features/bi/pages/BiCommandesPage").then((m) => ({ default: m.BiCommandesPage }))
);
const BiRevenuePage = lazy(() =>
  import("../../features/bi/pages/BiRevenuePage").then((m) => ({ default: m.BiRevenuePage }))
);
const BiArticlesPage = lazy(() =>
  import("../../features/bi/pages/BiArticlesPage").then((m) => ({ default: m.BiArticlesPage }))
);
const BiClientsPage = lazy(() =>
  import("../../features/bi/pages/BiClientsPage").then((m) => ({ default: m.BiClientsPage }))
);
const BiAchatsPage = lazy(() =>
  import("../../features/bi/pages/BiAchatsPage").then((m) => ({ default: m.BiAchatsPage }))
);

function PageLoader() {
  return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "center", flex: 1, minHeight: 240 }}>
      <span
        className="bi-spin"
        style={{ display: "block", width: 28, height: 28, borderRadius: "50%", border: "2.5px solid var(--border)", borderTopColor: "var(--accent)" }}
      />
    </div>
  );
}

export function AppShell({
  theme,
  density,
  setTheme,
  setDensity,
  session,
  onSignOut,
}: {
  theme: Theme;
  density: Density;
  setTheme: (theme: Theme) => void;
  setDensity: (density: Density) => void;
  session: AuthSession;
  onSignOut: () => void;
}) {
  const [collapsed, setCollapsed] = useStoredState("lpn-sidebar-collapsed", false);
  const [historiqueOpen, setHistoriqueOpen] = useStoredState("lpn-historique-open", false);
  const location = useLocation();
  const visibleMainNavItems = navItemsForRole(mainNavItems, session.appRole);
  const canViewDashboards = visibleMainNavItems.some((item) => item.to === "/tableau-de-bord");
  const title = navItems.find((item) => location.pathname.startsWith(item.to))?.label ?? "LPN AI";
  const isHistoriqueActive = location.pathname.startsWith("/historique");
  const hasActiveRoute = navItems.some((item) => location.pathname.startsWith(item.to));

  function toggleDarkMode() {
    setTheme(theme === "dark" ? "light" : "dark");
  }

  return (
    <div className={collapsed ? "app-shell app-shell-sidebar-collapsed" : "app-shell"}>
      <aside className={`${collapsed ? "sidebar sidebar-collapsed" : "sidebar"}${hasActiveRoute ? " sidebar-has-active" : ""}`}>
        <div className="sb-brand">
          <NavLink to="/conversation" className="sb-brand-link" aria-label="LPN AI-BI">
            <span className="sb-brand-mark" aria-hidden="true">
              <img src={robotIcon} alt="" className="sb-brand-mark-img" />
            </span>
            <span className="sb-brand-name">LPN AI-BI</span>
          </NavLink>
          <button className="sb-collapse-btn" onClick={() => setCollapsed(!collapsed)} aria-label="Basculer le menu">
            {collapsed ? <ChevronRight size={17} strokeWidth={2.5} /> : <ChevronLeft size={17} strokeWidth={2.5} />}
          </button>
        </div>

        <nav className="sb-nav" aria-label="Navigation principale">
          <span className="sb-section-label" data-short="B">AI-BI</span>
          {visibleMainNavItems.map((item) => (
            <SidebarLink key={item.to} item={item} />
          ))}
          <button
            className={`sb-nav-item sb-nav-dropdown-trigger${isHistoriqueActive ? " active" : ""}`}
            onClick={() => setHistoriqueOpen(!historiqueOpen)}
            aria-expanded={historiqueOpen}
          >
            <History size={20} strokeWidth={2.45} />
            <span>Historique</span>
            <ChevronDown size={14} className={`sb-chevron${historiqueOpen ? " sb-chevron-open" : ""}`} />
          </button>
          {historiqueOpen && !collapsed ? (
            <div className="sb-dropdown-children">
              {historiqueSubItems.map((sub) => (
                <NavLink key={sub.to} to={sub.to} end className="sb-nav-sub-item">
                  <span>{sub.label}</span>
                </NavLink>
              ))}
            </div>
          ) : null}
        </nav>

        {session.role === "admin" ? (
          <nav className="sb-nav sb-nav-secondary" aria-label="Administration">
            <span className="sb-section-label" data-short="A">ADMIN</span>
            <SidebarLink item={adminNavItem} />
          </nav>
        ) : null}

        <div className="sb-spacer" />

        <nav className="sb-nav sb-nav-bottom" aria-label="Système">
          <span className="sb-section-label" data-short="S">SYSTÈME</span>
          <SidebarLink item={settingsNavItem} />
          <button className="sb-nav-item sb-theme-row" type="button" onClick={toggleDarkMode} aria-pressed={theme === "dark"}>
            <Moon size={20} strokeWidth={2.45} />
            <span>Mode sombre</span>
            <span className={`sb-switch${theme === "dark" ? " sb-switch-on" : ""}`} aria-hidden="true">
              <span />
            </span>
          </button>
        </nav>

        <div className="sb-account">
          <div className="sb-account-avatar" aria-hidden="true">{session.username.slice(0, 1).toUpperCase()}</div>
          <div className="sb-account-info">
            <strong>{session.username}</strong>
            <span>{ROLE_LABELS[session.appRole] ?? "Utilisateur"}</span>
          </div>
        </div>
        <button className="sb-logout" onClick={onSignOut} type="button">
          <LogOut size={19} strokeWidth={2.35} />
          <span>Se déconnecter</span>
        </button>
      </aside>

      <main className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <Home size={14} />
            <ChevronRight size={13} />
            <span>{title}</span>
          </div>
          <div className="topbar-actions">
            <button className="command-trigger">
              <Command size={14} />
              <span>Recherche</span>
              <kbd>Ctrl K</kbd>
            </button>
            <ThemeBadge theme={theme} />
            <UserCircle size={18} />
            <button className="icon-button" onClick={onSignOut} aria-label="Se déconnecter" title="Se déconnecter">
              <LogOut size={16} />
            </button>
          </div>
        </header>

        <Suspense fallback={<PageLoader />}>
          <Routes>
            <Route path="/conversation" element={<ConversationPage session={session} />} />
            <Route path="/tableau-de-bord" element={canViewDashboards ? <Navigate to="/tableau-de-bord/vue-ensemble" replace /> : <Navigate to="/conversation" replace />} />
            <Route path="/tableau-de-bord/vue-ensemble" element={canViewDashboards ? <BiOverviewPage /> : <Navigate to="/conversation" replace />} />
            <Route path="/tableau-de-bord/commandes" element={canViewDashboards ? <BiCommandesPage /> : <Navigate to="/conversation" replace />} />
            <Route path="/tableau-de-bord/chiffre-affaires" element={canViewDashboards ? <BiRevenuePage /> : <Navigate to="/conversation" replace />} />
            <Route path="/tableau-de-bord/articles" element={canViewDashboards ? <BiArticlesPage /> : <Navigate to="/conversation" replace />} />
            <Route path="/tableau-de-bord/clients" element={canViewDashboards ? <BiClientsPage /> : <Navigate to="/conversation" replace />} />
            <Route path="/tableau-de-bord/achats" element={canViewDashboards ? <BiAchatsPage /> : <Navigate to="/conversation" replace />} />
            <Route path="/tableau-de-bord/commercial" element={<Navigate to="/tableau-de-bord/clients" replace />} />
            <Route path="/tableau-de-bord/analyse-vente" element={<Navigate to="/tableau-de-bord/vue-ensemble" replace />} />
            <Route path="/tableau-de-bord/:chartId" element={<Navigate to="/tableau-de-bord/vue-ensemble" replace />} />
            <Route path="/previsions" element={canViewDashboards ? <ForecastsPage /> : <Navigate to="/conversation" replace />} />
            <Route path="/planification-ia" element={canViewDashboards ? <PlanificationIaPage /> : <Navigate to="/conversation" replace />} />
            <Route path="/historique" element={<HistoryPage session={session} />} />
            <Route path="/admin" element={session.role === "admin" ? <AdminPage session={session} /> : <Navigate to="/conversation" replace />} />
            <Route
              path="/reglages"
              element={
                <SettingsPage theme={theme} density={density} setTheme={setTheme} setDensity={setDensity} />
              }
            />
            <Route path="*" element={<Navigate to="/tableau-de-bord" replace />} />
          </Routes>
        </Suspense>
      </main>

      <FloatingAssistant />
    </div>
  );
}
