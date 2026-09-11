package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ChatSessionRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private volatile boolean tableReady;

    ChatSessionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    void ensureTables() {
        if (tableReady) {
            return;
        }
        jdbcTemplate.execute(
                """
                CREATE SCHEMA IF NOT EXISTS app;
                CREATE TABLE IF NOT EXISTS app.ai_bi_chat_sessions (
                  id uuid PRIMARY KEY,
                  user_id uuid NOT NULL REFERENCES app.ai_bi_users(id) ON DELETE CASCADE,
                  title text NOT NULL,
                  created_at timestamptz NOT NULL DEFAULT now(),
                  updated_at timestamptz NOT NULL DEFAULT now()
                );
                CREATE INDEX IF NOT EXISTS idx_ai_bi_chat_sessions_user_updated
                  ON app.ai_bi_chat_sessions (user_id, updated_at DESC);
                CREATE TABLE IF NOT EXISTS app.ai_bi_chat_messages (
                  id uuid PRIMARY KEY,
                  session_id uuid NOT NULL REFERENCES app.ai_bi_chat_sessions(id) ON DELETE CASCADE,
                  role text NOT NULL CHECK (role IN ('user', 'assistant')),
                  content text NOT NULL,
                  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
                  created_at timestamptz NOT NULL DEFAULT now()
                );
                CREATE INDEX IF NOT EXISTS idx_ai_bi_chat_messages_session_created
                  ON app.ai_bi_chat_messages (session_id, created_at ASC);
                """);
        tableReady = true;
    }

    ChatSessionSummary createSession(UUID userId, String title) {
        ensureTables();
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO app.ai_bi_chat_sessions (id, user_id, title)
                VALUES (?, ?, ?)
                """,
                sessionId,
                userId,
                title);
        return findSummary(userId, sessionId).orElseThrow();
    }

    List<ChatSessionSummary> listSessions(UUID userId) {
        ensureTables();
        return jdbcTemplate.query(
                """
                SELECT s.id, s.title, s.created_at, s.updated_at, COUNT(m.id) AS message_count
                FROM app.ai_bi_chat_sessions s
                LEFT JOIN app.ai_bi_chat_messages m ON m.session_id = s.id
                WHERE s.user_id = ?
                GROUP BY s.id, s.title, s.created_at, s.updated_at
                ORDER BY s.updated_at DESC
                """,
                this::mapSummary,
                userId);
    }

    Optional<ChatSessionSummary> findSummary(UUID userId, UUID sessionId) {
        ensureTables();
        return jdbcTemplate.query(
                        """
                        SELECT s.id, s.title, s.created_at, s.updated_at, COUNT(m.id) AS message_count
                        FROM app.ai_bi_chat_sessions s
                        LEFT JOIN app.ai_bi_chat_messages m ON m.session_id = s.id
                        WHERE s.user_id = ?
                          AND s.id = ?
                        GROUP BY s.id, s.title, s.created_at, s.updated_at
                        """,
                        this::mapSummary,
                        userId,
                        sessionId)
                .stream()
                .findFirst();
    }

    Optional<ChatSessionDetail> findDetail(UUID userId, UUID sessionId) {
        Optional<ChatSessionSummary> summary = findSummary(userId, sessionId);
        if (summary.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ChatSessionDetail(summary.get(), listMessages(sessionId)));
    }

    ChatMessage addMessage(UUID userId, UUID sessionId, String role, String content, Map<String, Object> payload) {
        ensureTables();
        if (findSummary(userId, sessionId).isEmpty()) {
            throw new IllegalArgumentException("Conversation introuvable.");
        }
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO app.ai_bi_chat_messages (id, session_id, role, content, payload)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                """,
                messageId,
                sessionId,
                role,
                content,
                toJson(payload));
        jdbcTemplate.update(
                """
                UPDATE app.ai_bi_chat_sessions
                SET updated_at = now()
                WHERE id = ?
                  AND user_id = ?
                """,
                sessionId,
                userId);
        return listMessages(sessionId).stream()
                .filter(message -> message.id().equals(messageId))
                .findFirst()
                .orElseThrow();
    }

    Optional<ChatSessionSummary> renameSession(UUID userId, UUID sessionId, String title) {
        ensureTables();
        int updated = jdbcTemplate.update(
                """
                UPDATE app.ai_bi_chat_sessions
                SET title = ?,
                    updated_at = now()
                WHERE id = ?
                  AND user_id = ?
                """,
                title,
                sessionId,
                userId);
        return updated == 0 ? Optional.empty() : findSummary(userId, sessionId);
    }

    void deleteSession(UUID userId, UUID sessionId) {
        ensureTables();
        jdbcTemplate.update(
                """
                DELETE FROM app.ai_bi_chat_sessions
                WHERE id = ?
                  AND user_id = ?
                """,
                sessionId,
                userId);
    }

    private List<ChatMessage> listMessages(UUID sessionId) {
        return jdbcTemplate.query(
                """
                SELECT id, role, content, payload::text AS payload, created_at
                FROM app.ai_bi_chat_messages
                WHERE session_id = ?
                ORDER BY created_at ASC
                """,
                this::mapMessage,
                sessionId);
    }

    private ChatSessionSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ChatSessionSummary(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")),
                rs.getLong("message_count"));
    }

    private ChatMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return new ChatMessage(
                rs.getObject("id", UUID.class),
                rs.getString("role"),
                rs.getString("content"),
                fromJson(rs.getString("payload")),
                instant(rs.getTimestamp("created_at")));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Payload conversation invalide.", exception);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return json == null || json.isBlank() ? Map.of() : objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

record ChatSessionSummary(UUID id, String title, Instant createdAt, Instant updatedAt, long messageCount) {
}

record ChatMessage(UUID id, String role, String content, Map<String, Object> payload, Instant createdAt) {
}

record ChatSessionDetail(ChatSessionSummary session, List<ChatMessage> messages) {
}
