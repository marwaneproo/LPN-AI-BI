export type AuthRole = "admin" | "user";

// The seven LPN business roles used across the admin console and role-based
// navigation. "USER" is a legacy value kept only for accounts created by the
// old self-service signup flow; it is never offered as a choice in the UI.
export type AppRole = "ADMIN" | "DG" | "ACHATS" | "COMMERCIAL" | "LOGISTIQUE" | "DAF" | "CLIENT_B2B" | "USER";

export const ASSIGNABLE_ROLES: Exclude<AppRole, "USER">[] = [
  "ADMIN",
  "DG",
  "ACHATS",
  "COMMERCIAL",
  "LOGISTIQUE",
  "DAF",
  "CLIENT_B2B",
];

export const ROLE_LABELS: Record<AppRole, string> = {
  ADMIN: "Administrateur",
  DG: "Directeur Général",
  ACHATS: "Responsable Achats",
  COMMERCIAL: "Service Commercial",
  LOGISTIQUE: "Service Logistique",
  DAF: "Direction Admin. & Financière",
  CLIENT_B2B: "Client B2B",
  USER: "Utilisateur",
};

export type AuthSession = {
  username: string;
  role: AuthRole;
  /** Raw LPN business role (ADMIN, DG, ACHATS, ...), used for fine-grained navigation. */
  appRole: AppRole;
  fullName?: string | null;
};

export type AuthUser = {
  id: string;
  username: string;
  fullName?: string | null;
  role: string;
  roleLabel?: string;
  status: string;
  createdAt?: string | null;
  decidedAt?: string | null;
  decidedBy?: string | null;
  lastLoginAt?: string | null;
};

export type AuthResponse = {
  authenticated: boolean;
  status: string;
  user?: AuthUser | null;
  message: string;
};

export type AuditLogEntry = {
  actor: string;
  action: string;
  detail?: string | null;
  status: string;
  createdAt: string;
};

export type QaAuditEntry = {
  username: string;
  question: string;
  status: string;
  latencyMs?: number | null;
  createdAt: string;
};
