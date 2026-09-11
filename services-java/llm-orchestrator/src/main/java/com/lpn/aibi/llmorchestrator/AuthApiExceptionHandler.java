package com.lpn.aibi.llmorchestrator;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
class AuthApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), validationMessage(error.getField())));
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_ERROR",
                "Vérifiez les informations saisies.",
                fieldErrors,
                requestId(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "INVALID_REQUEST",
                "Le corps de la requête est invalide.",
                Map.of(),
                requestId(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "INVALID_REQUEST",
                exception.getMessage(),
                Map.of(),
                requestId(request)));
    }

    @ExceptionHandler(AuthForbiddenException.class)
    ResponseEntity<ErrorResponse> handleForbidden(AuthForbiddenException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(
                "FORBIDDEN",
                exception.getMessage(),
                Map.of(),
                requestId(request)));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
                .body(new ErrorResponse(
                        "RATE_LIMITED",
                        "Trop de tentatives. Réessayez dans quelques minutes.",
                        Map.of(),
                        requestId(request)));
    }

    private String validationMessage(String field) {
        return switch (field) {
            case "username" -> "Saisissez un identifiant valide de 3 caractères minimum.";
            case "password" -> "Saisissez un mot de passe conforme aux règles indiquées.";
            default -> "Valeur invalide.";
        };
    }

    private String requestId(HttpServletRequest request) {
        return Optional.ofNullable(request.getAttribute(AuthRequestContext.REQUEST_ID_ATTRIBUTE))
                .map(Object::toString)
                .orElse(null);
    }

    record ErrorResponse(String code, String message, Map<String, String> fieldErrors, String requestId) {
    }
}
