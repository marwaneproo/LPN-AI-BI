package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiSecurityFilter extends OncePerRequestFilter {

    static final String REQUEST_HEADER = "X-LPN-Request";
    private static final String REQUEST_HEADER_VALUE = "web";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,100}$");
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/v1/auth/login",
            "/v1/auth/signup",
            "/v1/auth/session",
            "/v1/auth/logout");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final AuthService authService;
    private final AuthSessionCookie sessionCookie;
    private final AuthProperties properties;
    private final ObjectMapper objectMapper;

    ApiSecurityFilter(
            AuthService authService,
            AuthSessionCookie sessionCookie,
            AuthProperties properties,
            ObjectMapper objectMapper) {
        this.authService = authService;
        this.sessionCookie = sessionCookie;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        request.setAttribute(AuthRequestContext.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        if (!properties.apiProtectionEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (PUBLIC_AUTH_PATHS.contains(request.getRequestURI())) {
            if (!SAFE_METHODS.contains(request.getMethod())
                    && !REQUEST_HEADER_VALUE.equals(request.getHeader(REQUEST_HEADER))) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "CSRF_CHECK_FAILED", "Requête refusée.", requestId);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        String token = sessionCookie.read(request).orElse(null);
        if (token == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_REQUIRED", "Session utilisateur requise.", requestId);
            return;
        }

        AuthUser user;
        try {
            user = authService.requireApprovedUser(token);
        } catch (IllegalArgumentException exception) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_REQUIRED", "Session expirée ou invalide.", requestId);
            return;
        }

        if (!SAFE_METHODS.contains(request.getMethod())
                && !REQUEST_HEADER_VALUE.equals(request.getHeader(REQUEST_HEADER))) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "CSRF_CHECK_FAILED", "Requête refusée.", requestId);
            return;
        }

        request.setAttribute(AuthRequestContext.SESSION_TOKEN_ATTRIBUTE, token);
        request.setAttribute(AuthRequestContext.USER_ATTRIBUTE, user);
        filterChain.doFilter(request, response);
    }

    private String requestId(String candidate) {
        return candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String code,
            String message,
            String requestId) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "code", code,
                "message", message,
                "requestId", requestId));
    }
}
