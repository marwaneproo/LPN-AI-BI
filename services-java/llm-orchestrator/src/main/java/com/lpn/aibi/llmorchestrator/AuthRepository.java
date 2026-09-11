package com.lpn.aibi.llmorchestrator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AuthRepository {

    private final JdbcTemplate jdbcTemplate;
    private volatile boolean tableReady;

    AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    synchronized void ensureTable() {
        if (tableReady) {
            return;
        }
        jdbcTemplate.execute(
                """
                CREATE SCHEMA IF NOT EXISTS app;
                CREATE TABLE IF NOT EXISTS app.ai_bi_users (
                  id uuid PRIMARY KEY,
                  username text NOT NULL UNIQUE,
                  password_hash text NOT NULL,
                  password_salt text NOT NULL DEFAULT '',
                  role text NOT NULL CHECK (role IN ('ADMIN', 'USER')),
                  status text NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
                  created_at timestamptz NOT NULL DEFAULT now(),
                  decided_at timestamptz,
                  decided_by text
                );
                CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_bi_users_normalized_username
                  ON app.ai_bi_users (lower(username));
                CREATE INDEX IF NOT EXISTS idx_ai_bi_users_status
                  ON app.ai_bi_users (status, created_at DESC);
                CREATE TABLE IF NOT EXISTS app.ai_bi_sessions (
                  token uuid PRIMARY KEY,
                  token_hash char(64),
                  user_id uuid NOT NULL REFERENCES app.ai_bi_users(id) ON DELETE CASCADE,
                  created_at timestamptz NOT NULL DEFAULT now(),
                  expires_at timestamptz NOT NULL
                );
                ALTER TABLE app.ai_bi_sessions
                  ADD COLUMN IF NOT EXISTS token_hash char(64);
                CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_bi_sessions_token_hash
                  ON app.ai_bi_sessions (token_hash)
                  WHERE token_hash IS NOT NULL;
                CREATE INDEX IF NOT EXISTS idx_ai_bi_sessions_user_id
                  ON app.ai_bi_sessions (user_id);
                CREATE INDEX IF NOT EXISTS idx_ai_bi_sessions_expires_at
                  ON app.ai_bi_sessions (expires_at);
                """);

        // --- LPN roles / admin console additions (kept as a second, additive step so the
        // original bootstrap statements above stay untouched for existing installations). ---
        jdbcTemplate.execute(
                """
                ALTER TABLE app.ai_bi_users ADD COLUMN IF NOT EXISTS full_name text;
                ALTER TABLE app.ai_bi_users ADD COLUMN IF NOT EXISTS last_login_at timestamptz;
                ALTER TABLE app.ai_bi_users DROP CONSTRAINT IF EXISTS ai_bi_users_role_check;
                ALTER TABLE app.ai_bi_users ADD CONSTRAINT ai_bi_users_role_check
                  CHECK (role IN ('ADMIN', 'DG', 'ACHATS', 'COMMERCIAL', 'LOGISTIQUE', 'DAF', 'CLIENT_B2B', 'USER'));
                ALTER TABLE app.ai_bi_users DROP CONSTRAINT IF EXISTS ai_bi_users_status_check;
                ALTER TABLE app.ai_bi_users ADD CONSTRAINT ai_bi_users_status_check
                  CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'DISABLED'));
                CREATE TABLE IF NOT EXISTS app.ai_bi_audit_log (
                  id uuid PRIMARY KEY,
                  actor text NOT NULL,
                  action text NOT NULL,
                  detail text,
                  status text NOT NULL DEFAULT 'OK',
                  created_at timestamptz NOT NULL DEFAULT now()
                );
                CREATE INDEX IF NOT EXISTS idx_ai_bi_audit_log_created_at
                  ON app.ai_bi_audit_log (created_at DESC);
                CREATE TABLE IF NOT EXISTS app.ai_bi_qa_audit (
                  id uuid PRIMARY KEY,
                  username text NOT NULL,
                  question text NOT NULL,
                  status text NOT NULL,
                  latency_ms bigint,
                  created_at timestamptz NOT NULL DEFAULT now()
                );
                CREATE INDEX IF NOT EXISTS idx_ai_bi_qa_audit_created_at
                  ON app.ai_bi_qa_audit (created_at DESC);
                """);
        tableReady = true;
    }

    private static final String USER_COLUMNS =
            "id, username, full_name, password_hash, password_salt, role, status, "
            + "created_at, decided_at, decided_by, last_login_at";

    Optional<AuthUserWithSecret> findByUsername(String username) {
        ensureTable();
        return jdbcTemplate.query(
                        "SELECT " + USER_COLUMNS + " FROM app.ai_bi_users WHERE lower(username) = lower(?)",
                        this::mapUserWithSecret,
                        username)
                .stream()
                .findFirst();
    }

    Optional<AuthUser> createPendingUser(String username, String passwordHash, String passwordSalt) {
        ensureTable();
        UUID id = UUID.randomUUID();
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO app.ai_bi_users (id, username, password_hash, password_salt, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'PENDING')
                ON CONFLICT DO NOTHING
                """,
                id,
                username,
                passwordHash,
                passwordSalt);
        return inserted == 0 ? Optional.empty() : findById(id);
    }

    Optional<AuthUser> createUserByAdmin(
            String username, String fullName, String passwordHash, String passwordSalt, String role, String createdBy) {
        ensureTable();
        UUID id = UUID.randomUUID();
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO app.ai_bi_users
                  (id, username, full_name, password_hash, password_salt, role, status, decided_at, decided_by)
                VALUES (?, ?, ?, ?, ?, ?, 'APPROVED', now(), ?)
                ON CONFLICT DO NOTHING
                """,
                id,
                username,
                fullName,
                passwordHash,
                passwordSalt,
                role,
                createdBy);
        return inserted == 0 ? Optional.empty() : findById(id);
    }

    void updatePasswordSecret(UUID userId, String passwordHash, String passwordSalt) {
        ensureTable();
        jdbcTemplate.update(
                """
                UPDATE app.ai_bi_users
                SET password_hash = ?, password_salt = ?
                WHERE id = ?
                """,
                passwordHash,
                passwordSalt,
                userId);
    }

    void upsertAdmin(String username, String passwordHash, String passwordSalt) {
        ensureTable();
        int updated = jdbcTemplate.update(
                """
                UPDATE app.ai_bi_users
                SET username = ?,
                    password_hash = ?,
                    password_salt = ?,
                    role = 'ADMIN',
                    status = 'APPROVED',
                    decided_at = now(),
                    decided_by = 'system'
                WHERE lower(username) = lower(?)
                """,
                username,
                passwordHash,
                passwordSalt,
                username);
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO app.ai_bi_users
                      (id, username, password_hash, password_salt, role, status, decided_at, decided_by)
                    VALUES (?, ?, ?, ?, 'ADMIN', 'APPROVED', now(), 'system')
                    """,
                    UUID.randomUUID(),
                    username,
                    passwordHash,
                    passwordSalt);
        }
    }

    List<AuthUser> pendingUsers() {
        ensureTable();
        return jdbcTemplate.query(
                "SELECT " + USER_COLUMNS + " FROM app.ai_bi_users WHERE status = 'PENDING' ORDER BY created_at DESC",
                (rs, rowNum) -> mapUserWithSecret(rs, rowNum).user());
    }

    List<AuthUser> listUsers(String search) {
        ensureTable();
        String trimmed = search == null ? "" : search.trim();
        String like = "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
        return jdbcTemplate.query(
                "SELECT " + USER_COLUMNS + " FROM app.ai_bi_users "
                        + "WHERE (?::text = '' OR lower(username) LIKE ? OR lower(coalesce(full_name, '')) LIKE ?) "
                        + "ORDER BY created_at DESC",
                (rs, rowNum) -> mapUserWithSecret(rs, rowNum).user(),
                trimmed,
                like,
                like);
    }

    Optional<AuthUser> decide(UUID userId, boolean approved, String adminUsername) {
        ensureTable();
        int updated = jdbcTemplate.update(
                """
                UPDATE app.ai_bi_users
                SET status = ?,
                    decided_at = now(),
                    decided_by = ?
                WHERE id = ?
                  AND role <> 'ADMIN'
                """,
                approved ? "APPROVED" : "REJECTED",
                adminUsername,
                userId);
        if (updated == 0) {
            return Optional.empty();
        }
        if (!approved) {
            revokeUserSessions(userId);
        }
        return findById(userId);
    }

    Optional<AuthUser> updateRole(UUID userId, String role) {
        ensureTable();
        int updated = jdbcTemplate.update(
                "UPDATE app.ai_bi_users SET role = ? WHERE id = ? AND role <> 'ADMIN'",
                role,
                userId);
        return updated == 0 ? Optional.empty() : findById(userId);
    }

    Optional<AuthUser> updateStatus(UUID userId, String status) {
        ensureTable();
        int updated = jdbcTemplate.update(
                "UPDATE app.ai_bi_users SET status = ? WHERE id = ? AND role <> 'ADMIN'",
                status,
                userId);
        if (updated == 0) {
            return Optional.empty();
        }
        if (!"APPROVED".equals(status)) {
            revokeUserSessions(userId);
        }
        return findById(userId);
    }

    boolean deleteUser(UUID userId) {
        ensureTable();
        int deleted = jdbcTemplate.update(
                "DELETE FROM app.ai_bi_users WHERE id = ? AND role <> 'ADMIN'",
                userId);
        return deleted > 0;
    }

    void touchLastLogin(UUID userId) {
        ensureTable();
        jdbcTemplate.update("UPDATE app.ai_bi_users SET last_login_at = now() WHERE id = ?", userId);
    }

    void createSession(AuthUser user, String tokenHash, Instant expiresAt) {
        ensureTable();
        deleteExpiredSessions();
        revokeUserSessions(user.id());
        jdbcTemplate.update(
                """
                INSERT INTO app.ai_bi_sessions (token, token_hash, user_id, expires_at)
                VALUES (?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tokenHash,
                user.id(),
                Timestamp.from(expiresAt));
    }

    Optional<AuthUser> findBySessionTokenHash(String tokenHash) {
        ensureTable();
        return jdbcTemplate.query(
                        "SELECT u.id, u.username, u.full_name, u.password_hash, u.password_salt, u.role, u.status, "
                                + "u.created_at, u.decided_at, u.decided_by, u.last_login_at "
                                + "FROM app.ai_bi_sessions s "
                                + "JOIN app.ai_bi_users u ON u.id = s.user_id "
                                + "WHERE s.token_hash = ? AND s.expires_at > now() AND u.status = 'APPROVED'",
                        (rs, rowNum) -> mapUserWithSecret(rs, rowNum).user(),
                        tokenHash)
                .stream()
                .findFirst();
    }

    void revokeSession(String tokenHash) {
        ensureTable();
        jdbcTemplate.update("DELETE FROM app.ai_bi_sessions WHERE token_hash = ?", tokenHash);
    }

    private void revokeUserSessions(UUID userId) {
        jdbcTemplate.update("DELETE FROM app.ai_bi_sessions WHERE user_id = ?", userId);
    }

    private void deleteExpiredSessions() {
        jdbcTemplate.update("DELETE FROM app.ai_bi_sessions WHERE expires_at <= now()");
    }

    private Optional<AuthUser> findById(UUID userId) {
        return jdbcTemplate.query(
                        "SELECT " + USER_COLUMNS + " FROM app.ai_bi_users WHERE id = ?",
                        (rs, rowNum) -> mapUserWithSecret(rs, rowNum).user(),
                        userId)
                .stream()
                .findFirst();
    }

    private AuthUserWithSecret mapUserWithSecret(ResultSet rs, int rowNum) throws SQLException {
        AuthUser user = new AuthUser(
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("role"),
                rs.getString("status"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("decided_at")),
                rs.getString("decided_by"),
                instant(rs.getTimestamp("last_login_at")));
        return new AuthUserWithSecret(user, rs.getString("password_hash"), rs.getString("password_salt"));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    // --- Audit log: connections, logouts, role/status changes and admin actions ---

    void logAuditEvent(String actor, String action, String detail, String status) {
        ensureTable();
        jdbcTemplate.update(
                "INSERT INTO app.ai_bi_audit_log (id, actor, action, detail, status) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                actor,
                action,
                detail,
                status);
    }

    List<AuditLogEntry> recentAuditLog(int limit) {
        ensureTable();
        return jdbcTemplate.query(
                "SELECT actor, action, detail, status, created_at FROM app.ai_bi_audit_log "
                        + "ORDER BY created_at DESC LIMIT ?",
                (rs, rowNum) -> new AuditLogEntry(
                        rs.getString("actor"),
                        rs.getString("action"),
                        rs.getString("detail"),
                        rs.getString("status"),
                        instant(rs.getTimestamp("created_at"))),
                limit);
    }

    // --- Assistant request audit ("Journal des requêtes") ---

    void logQaEvent(String username, String question, String status, Long latencyMs) {
        ensureTable();
        jdbcTemplate.update(
                "INSERT INTO app.ai_bi_qa_audit (id, username, question, status, latency_ms) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                username,
                question,
                status,
                latencyMs);
    }

    List<QaAuditEntry> recentQaAudit(int limit) {
        ensureTable();
        return jdbcTemplate.query(
                "SELECT username, question, status, latency_ms, created_at FROM app.ai_bi_qa_audit "
                        + "ORDER BY created_at DESC LIMIT ?",
                (rs, rowNum) -> new QaAuditEntry(
                        rs.getString("username"),
                        rs.getString("question"),
                        rs.getString("status"),
                        (Long) rs.getObject("latency_ms"),
                        instant(rs.getTimestamp("created_at"))),
                limit);
    }

    record AuthUserWithSecret(AuthUser user, String passwordHash, String passwordSalt) {
    }

    record AuditLogEntry(String actor, String action, String detail, String status, Instant createdAt) {
    }

    record QaAuditEntry(String username, String question, String status, Long latencyMs, Instant createdAt) {
    }
}
