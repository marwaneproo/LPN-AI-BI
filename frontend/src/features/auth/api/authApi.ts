import { API_BASE_URL, apiFetch } from "../../../api/client";
import type {
  AppRole,
  AuditLogEntry,
  AuthResponse,
  AuthSession,
  AuthUser,
  QaAuditEntry,
} from "../types/auth.types";

type ApiError = {
  message?: string;
  fieldErrors?: Record<string, string>;
};

async function readAuthResponse(response: Response): Promise<AuthResponse> {
  const payload = (await response.json().catch(() => null)) as AuthResponse | ApiError | null;
  if (payload && "status" in payload) {
    return payload;
  }
  const message = payload && "message" in payload && payload.message
    ? payload.message
    : `La requête d'authentification a échoué (${response.status}).`;
  throw new Error(message);
}

export async function login(username: string, password: string, rememberMe: boolean): Promise<AuthResponse> {
  const response = await apiFetch(`${API_BASE_URL}/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password, rememberMe }),
  });
  return readAuthResponse(response);
}

export async function signUp(username: string, password: string): Promise<AuthResponse> {
  const response = await apiFetch(`${API_BASE_URL}/v1/auth/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  return readAuthResponse(response);
}

export async function getCurrentSession(): Promise<AuthSession | null> {
  const response = await apiFetch(`${API_BASE_URL}/v1/auth/session`);
  if (response.status === 401) return null;
  const payload = await readAuthResponse(response);
  return payload.authenticated && payload.user ? toSession(payload.user) : null;
}

export async function logout(): Promise<void> {
  await apiFetch(`${API_BASE_URL}/v1/auth/logout`, { method: "POST" });
}

export async function fetchPendingUsers(_session: AuthSession): Promise<AuthUser[]> {
  const response = await apiFetch(`${API_BASE_URL}/v1/auth/pending-users`);
  if (!response.ok) {
    throw new Error("Accès administrateur requis pour charger les demandes.");
  }
  return (await response.json()) as AuthUser[];
}

export async function decidePendingUser(
  _session: AuthSession,
  userId: string,
  approved: boolean,
): Promise<AuthUser> {
  const response = await apiFetch(`${API_BASE_URL}/v1/auth/users/${userId}/decision`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ approved }),
  });
  if (!response.ok) {
    throw new Error("La décision administrateur n'a pas pu être enregistrée.");
  }
  return (await response.json()) as AuthUser;
}

export function toSession(user: AuthUser): AuthSession {
  return {
    username: user.username,
    role: user.role === "ADMIN" ? "admin" : "user",
    appRole: (user.role as AppRole) ?? "USER",
    fullName: user.fullName,
  };
}

// ---------------------------------------------------------------------------
// Admin console: user provisioning, role & status management, audit trail
// ---------------------------------------------------------------------------

async function readJsonOrThrow<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as ApiError | null;
    throw new Error(payload?.message || fallbackMessage);
  }
  return (await response.json()) as T;
}

export async function adminListUsers(search = ""): Promise<AuthUser[]> {
  const query = search.trim() ? `?q=${encodeURIComponent(search.trim())}` : "";
  const response = await apiFetch(`${API_BASE_URL}/v1/auth/admin/users${query}`);
  return readJsonOrThrow<AuthUser[]>(response, "Impossible de charger la liste des utilisateurs.");
}

export async function adminCreateUser(input: {
  fullName: string;
  username: string;
  password: string;
  role: string;
}): Promise<AuthUser> {
  const response = await apiFetch(`${API_BASE_URL}/v1/auth/admin/users`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return readJsonOrThrow<AuthUser>(response, "La création du compte a échoué.");
}

export async function adminUpdateRole(userId: string, role: string): Promise<AuthUser> {
  const response = await apiFetch(`${API_BASE_URL}/v1/auth/admin/users/${userId}/role`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ role }),
  });
  return readJsonOrThrow<AuthUser>(response, "La mise à jour du rôle a échoué.");
}

export async function adminUpdateStatus(userId: string, enabled: boolean): Promise<AuthUser> {
  const response = await apiFetch(`${API_BASE_URL}/v1/auth/admin/users/${userId}/status`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ enabled }),
  });
  return readJsonOrThrow<AuthUser>(response, "La mise à jour du statut a échoué.");
}

export async function adminDeleteUser(userId: string): Promise<void> {
  const response = await apiFetch(`${API_BASE_URL}/v1/auth/admin/users/${userId}`, { method: "DELETE" });
  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as ApiError | null;
    throw new Error(payload?.message || "La suppression du compte a échoué.");
  }
}

export async function adminAuditLog(limit = 50): Promise<AuditLogEntry[]> {
  const response = await apiFetch(`${API_BASE_URL}/v1/auth/admin/audit-log?limit=${limit}`);
  return readJsonOrThrow<AuditLogEntry[]>(response, "Impossible de charger le journal d'audit.");
}

export async function adminQaAudit(limit = 50): Promise<QaAuditEntry[]> {
  const response = await apiFetch(`${API_BASE_URL}/v1/auth/admin/qa-audit?limit=${limit}`);
  return readJsonOrThrow<QaAuditEntry[]>(response, "Impossible de charger le journal des requêtes.");
}
