package com.lpn.aibi.llmorchestrator;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class ChatSessionService {

    private final AuthService authService;
    private final ChatSessionRepository chatSessionRepository;

    ChatSessionService(AuthService authService, ChatSessionRepository chatSessionRepository) {
        this.authService = authService;
        this.chatSessionRepository = chatSessionRepository;
    }

    List<ChatSessionSummary> listSessions(String token) {
        AuthUser user = authService.requireApprovedUser(token);
        return chatSessionRepository.listSessions(user.id());
    }

    ChatSessionSummary createSession(String token, String title) {
        AuthUser user = authService.requireApprovedUser(token);
        return chatSessionRepository.createSession(user.id(), cleanTitle(title));
    }

    ChatSessionDetail getSession(String token, UUID sessionId) {
        AuthUser user = authService.requireApprovedUser(token);
        return chatSessionRepository.findDetail(user.id(), sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation introuvable."));
    }

    ChatMessage addMessage(String token, UUID sessionId, String role, String content, Map<String, Object> payload) {
        AuthUser user = authService.requireApprovedUser(token);
        String cleanRole = cleanRole(role);
        String cleanContent = content == null ? "" : content.trim();
        if (cleanContent.isBlank()) {
            throw new IllegalArgumentException("Message vide.");
        }
        return chatSessionRepository.addMessage(user.id(), sessionId, cleanRole, cleanContent, payload);
    }

    ChatSessionSummary renameSession(String token, UUID sessionId, String title) {
        AuthUser user = authService.requireApprovedUser(token);
        return chatSessionRepository.renameSession(user.id(), sessionId, cleanTitle(title))
                .orElseThrow(() -> new IllegalArgumentException("Conversation introuvable."));
    }

    void deleteSession(String token, UUID sessionId) {
        AuthUser user = authService.requireApprovedUser(token);
        chatSessionRepository.deleteSession(user.id(), sessionId);
    }

    private String cleanRole(String role) {
        if ("user".equals(role) || "assistant".equals(role)) {
            return role;
        }
        throw new IllegalArgumentException("Role message invalide.");
    }

    private String cleanTitle(String title) {
        String clean = title == null ? "" : title.trim().replaceAll("\\s+", " ");
        if (clean.isBlank()) {
            return "Nouvelle conversation";
        }
        return clean.length() > 72 ? clean.substring(0, 72).trim() : clean;
    }
}
