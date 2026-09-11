package com.lpn.aibi.llmorchestrator;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class QaController {

    private final QaService qaService;
    private final AuthRepository authRepository;

    QaController(QaService qaService, AuthRepository authRepository) {
        this.qaService = qaService;
        this.authRepository = authRepository;
    }

    @PostMapping("/v1/qa")
    QaService.QaResponse answer(@RequestBody QaService.QaRequest request, HttpServletRequest servletRequest) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question is required");
        }
        AuthUser currentUser = AuthRequestContext.currentUserOrNull(servletRequest);
        String actor = currentUser != null ? currentUser.username() : "inconnu";
        // Audit-log exception: ADMIN-role users are never recorded in the request/audit log — neither their
        // question nor the associated response/status. Every other role keeps logging exactly as before.
        boolean skipAuditLog = currentUser != null && "ADMIN".equals(currentUser.role());
        try {
            QaService.QaResponse response = qaService.answer(request);
            String status = response.executionStatus() != null ? response.executionStatus() : "OK";
            if (!skipAuditLog) {
                authRepository.logQaEvent(actor, request.question(), status, response.latencyMs());
            }
            return response;
        } catch (RuntimeException exception) {
            if (!skipAuditLog) {
                authRepository.logQaEvent(actor, request.question(), "ERREUR", null);
            }
            throw exception;
        }
    }
}
