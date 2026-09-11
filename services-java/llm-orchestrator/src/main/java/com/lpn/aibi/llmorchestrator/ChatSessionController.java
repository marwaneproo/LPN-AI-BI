package com.lpn.aibi.llmorchestrator;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/chat-sessions")
class ChatSessionController {

    private final ChatSessionService chatSessionService;

    ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @GetMapping
    List<ChatSessionSummary> listSessions(HttpServletRequest request) {
        return chatSessionService.listSessions(AuthRequestContext.sessionToken(request));
    }

    @PostMapping
    ChatSessionSummary createSession(@RequestBody CreateChatSessionRequest request, HttpServletRequest servletRequest) {
        return chatSessionService.createSession(AuthRequestContext.sessionToken(servletRequest), request.title());
    }

    @GetMapping("/{id}")
    ChatSessionDetail getSession(@PathVariable UUID id, HttpServletRequest request) {
        return chatSessionService.getSession(AuthRequestContext.sessionToken(request), id);
    }

    @PostMapping("/{id}/messages")
    ChatMessage addMessage(
            @PathVariable UUID id,
            @RequestBody SaveChatMessageRequest request,
            HttpServletRequest servletRequest) {
        return chatSessionService.addMessage(
                AuthRequestContext.sessionToken(servletRequest), id, request.role(), request.content(), request.payload());
    }

    @PatchMapping("/{id}")
    ChatSessionSummary renameSession(
            @PathVariable UUID id,
            @RequestBody RenameChatSessionRequest request,
            HttpServletRequest servletRequest) {
        return chatSessionService.renameSession(AuthRequestContext.sessionToken(servletRequest), id, request.title());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteSession(@PathVariable UUID id, HttpServletRequest request) {
        chatSessionService.deleteSession(AuthRequestContext.sessionToken(request), id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", exception.getMessage()));
    }

    record CreateChatSessionRequest(String title) {
    }

    record RenameChatSessionRequest(String title) {
    }

    record SaveChatMessageRequest(String role, String content, Map<String, Object> payload) {
    }
}
