import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Ban, CheckCircle2, Eye, EyeOff, Search, ShieldCheck, Trash2, UserPlus } from "lucide-react";
import { Card, CardTitle } from "../../../components/ui/Card";
import { DataTable } from "../../../components/ui/DataTable";
import { PageFrame } from "../../../components/layout/PageFrame";
import type { AppRole, AuditLogEntry, AuthSession, AuthUser, QaAuditEntry } from "../../auth/types/auth.types";
import { ASSIGNABLE_ROLES, ROLE_LABELS } from "../../auth/types/auth.types";
import {
  adminAuditLog,
  adminCreateUser,
  adminDeleteUser,
  adminListUsers,
  adminQaAudit,
  adminUpdateRole,
  adminUpdateStatus,
} from "../../auth/api/authApi";

function formatDate(value?: string | null) {
  if (!value) return "Jamais";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Jamais";
  return date.toLocaleString("fr-FR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function AdminPage({ session }: { session: AuthSession }) {
  const [users, setUsers] = useState<AuthUser[]>([]);
  const [search, setSearch] = useState("");
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [usersError, setUsersError] = useState("");

  const [fullName, setFullName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [role, setRole] = useState<AppRole>("COMMERCIAL");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [createSuccess, setCreateSuccess] = useState("");

  const [auditLog, setAuditLog] = useState<AuditLogEntry[]>([]);
  const [qaAudit, setQaAudit] = useState<QaAuditEntry[]>([]);
  const [auditError, setAuditError] = useState("");

  async function refreshUsers(query = search) {
    setLoadingUsers(true);
    setUsersError("");
    try {
      setUsers(await adminListUsers(query));
    } catch (error) {
      setUsersError(error instanceof Error ? error.message : "Impossible de charger les comptes.");
    } finally {
      setLoadingUsers(false);
    }
  }

  async function refreshAuditLogs() {
    setAuditError("");
    try {
      const [logs, qa] = await Promise.all([adminAuditLog(50), adminQaAudit(50)]);
      setAuditLog(logs);
      setQaAudit(qa);
    } catch (error) {
      setAuditError(error instanceof Error ? error.message : "Impossible de charger les journaux d'audit.");
    }
  }

  useEffect(() => {
    void refreshUsers("");
    void refreshAuditLogs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onCreateUser(event: FormEvent) {
    event.preventDefault();
    if (creating) return;
    setCreateError("");
    setCreateSuccess("");
    setCreating(true);
    try {
      const created = await adminCreateUser({ fullName, username, password, role });
      setCreateSuccess(`Compte ${created.username} créé avec succès.`);
      setFullName("");
      setUsername("");
      setPassword("");
      await refreshUsers();
      await refreshAuditLogs();
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : "La création du compte a échoué.");
    } finally {
      setCreating(false);
    }
  }

  async function onRoleChange(user: AuthUser, nextRole: string) {
    try {
      const updated = await adminUpdateRole(user.id, nextRole);
      setUsers((current) => current.map((item) => (item.id === user.id ? updated : item)));
      void refreshAuditLogs();
    } catch (error) {
      setUsersError(error instanceof Error ? error.message : "La mise à jour du rôle a échoué.");
    }
  }

  async function onToggleStatus(user: AuthUser) {
    try {
      const updated = await adminUpdateStatus(user.id, user.status !== "APPROVED");
      setUsers((current) => current.map((item) => (item.id === user.id ? updated : item)));
      void refreshAuditLogs();
    } catch (error) {
      setUsersError(error instanceof Error ? error.message : "La mise à jour du statut a échoué.");
    }
  }

  async function onDeleteUser(user: AuthUser) {
    if (!window.confirm(`Supprimer définitivement le compte ${user.username} ?`)) return;
    try {
      await adminDeleteUser(user.id);
      setUsers((current) => current.filter((item) => item.id !== user.id));
      void refreshAuditLogs();
    } catch (error) {
      setUsersError(error instanceof Error ? error.message : "La suppression du compte a échoué.");
    }
  }

  const auditRows = useMemo(
    () =>
      auditLog.map((entry) => ({
        Horodatage: formatDate(entry.createdAt),
        Acteur: entry.actor,
        Action: entry.action,
        Détail: entry.detail ?? "—",
        Statut: entry.status,
      })),
    [auditLog],
  );

  const qaRows = useMemo(
    () =>
      qaAudit.map((entry) => ({
        Heure: formatDate(entry.createdAt),
        Utilisateur: entry.username,
        Question: entry.question,
        Statut: entry.status,
        "Latence (ms)": entry.latencyMs ?? "—",
      })),
    [qaAudit],
  );

  return (
    <PageFrame title="Administration" subtitle="Comptes, rôles et journaux d'audit de la plateforme LPN AI-BI.">
      <Card>
        <CardTitle title="Créer un utilisateur" subtitle="Aucune inscription libre : c'est l'administrateur qui provisionne chaque compte et lui attribue un rôle." />
        <form onSubmit={onCreateUser} className="lb-card !shadow-none !border-0 !rounded-none p-0">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Nom complet</label>
              <input
                className="lb-input-field"
                placeholder="Prénom Nom"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
                disabled={creating}
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Email professionnel</label>
              <input
                className="lb-input-field"
                placeholder="prenom.nom@lpn.ma"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                disabled={creating}
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Rôle</label>
              <select
                className="lb-input-field"
                value={role}
                onChange={(e) => setRole(e.target.value as AppRole)}
                disabled={creating}
              >
                {ASSIGNABLE_ROLES.map((r) => (
                  <option key={r} value={r}>
                    {ROLE_LABELS[r]}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Mot de passe temporaire</label>
              <div className="relative">
                <input
                  className="lb-input-field pr-10"
                  type={showPassword ? "text" : "password"}
                  placeholder="12 caractères min."
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  minLength={12}
                  disabled={creating}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((s) => !s)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>
          </div>
          {createError ? <p className="mt-3 text-sm text-alert">{createError}</p> : null}
          {createSuccess ? <p className="mt-3 text-sm text-positive">{createSuccess}</p> : null}
          <button type="submit" className="lb-btn-primary mt-4" disabled={creating}>
            <UserPlus size={16} /> {creating ? "Création…" : "Créer le compte"}
          </button>
        </form>
      </Card>

      <Card>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <CardTitle title="Gestion des utilisateurs & des rôles" subtitle={`${users.length} compte(s) provisionné(s)`} />
          <div className="relative">
            <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              className="lb-input-field !py-1.5 !pl-8 text-sm"
              placeholder="Rechercher un utilisateur…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") void refreshUsers();
              }}
            />
          </div>
        </div>
        {usersError ? <div className="admin-status">{usersError}</div> : null}
        <div className="table-wrap mt-3">
          <table>
            <thead>
              <tr>
                <th>Utilisateur</th>
                <th>Rôle</th>
                <th>Statut</th>
                <th>Dernière connexion</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => {
                const isMainAdmin = user.role === "ADMIN";
                return (
                  <tr key={user.id}>
                    <td>
                      <strong>{user.fullName || user.username}</strong>
                      <br />
                      <span style={{ color: "var(--text-secondary)" }}>{user.username}</span>
                    </td>
                    <td>
                      <select
                        className="lb-input-field !py-1 !w-auto text-sm"
                        value={user.role}
                        disabled={isMainAdmin}
                        onChange={(e) => void onRoleChange(user, e.target.value)}
                      >
                        {(isMainAdmin ? (["ADMIN", ...ASSIGNABLE_ROLES] as const) : ASSIGNABLE_ROLES).map((r) => (
                          <option key={r} value={r}>
                            {ROLE_LABELS[r as AppRole] ?? r}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <span className={`lb-badge ${user.status === "APPROVED" ? "bg-positive/10 text-positive" : "bg-alert/10 text-alert"}`}>
                        {user.status === "APPROVED" ? "Actif" : user.status === "DISABLED" ? "Désactivé" : user.status}
                      </span>
                    </td>
                    <td>{formatDate(user.lastLoginAt)}</td>
                    <td>
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          title={user.status === "APPROVED" ? "Désactiver" : "Réactiver"}
                          onClick={() => void onToggleStatus(user)}
                          disabled={isMainAdmin}
                          className="rounded-lg border border-slate-200 p-1.5 text-slate-500 hover:border-amber-300 hover:text-amber-600 disabled:opacity-30"
                        >
                          {user.status === "APPROVED" ? <Ban size={15} /> : <CheckCircle2 size={15} />}
                        </button>
                        <button
                          type="button"
                          title="Supprimer"
                          onClick={() => void onDeleteUser(user)}
                          disabled={isMainAdmin || user.username === session.username}
                          className="rounded-lg border border-slate-200 p-1.5 text-slate-500 hover:border-alert/40 hover:text-alert disabled:opacity-30"
                        >
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
              {!users.length && !loadingUsers ? (
                <tr>
                  <td colSpan={5} style={{ textAlign: "center", color: "var(--text-secondary)" }}>
                    Aucun utilisateur trouvé.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </Card>

      <Card>
        <CardTitle title="Journal d'audit" subtitle="Connexions, changements de rôle et actions administratives" />
        {auditError ? <div className="admin-status">{auditError}</div> : null}
        {auditRows.length ? (
          <DataTable rows={auditRows} />
        ) : (
          <div className="empty-approval">
            <ShieldCheck size={22} />
            <strong>Aucun évènement pour le moment</strong>
          </div>
        )}
      </Card>

      <Card>
        <CardTitle
          title="Journal d'audit (Audit des requêtes)"
          subtitle="Toutes les requêtes envoyées à l'Assistant IA, toutes pages confondues — visible uniquement par l'Administrateur."
        />
        {qaRows.length ? (
          <DataTable rows={qaRows} />
        ) : (
          <div className="empty-approval">
            <ShieldCheck size={22} />
            <strong>Aucune requête envoyée à l'Assistant IA pour le moment.</strong>
          </div>
        )}
      </Card>
    </PageFrame>
  );
}
