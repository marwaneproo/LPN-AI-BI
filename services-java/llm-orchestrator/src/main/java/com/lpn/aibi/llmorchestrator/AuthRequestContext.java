package com.lpn.aibi.llmorchestrator;

import jakarta.servlet.http.HttpServletRequest;

final class AuthRequestContext {

    static final String SESSION_TOKEN_ATTRIBUTE = AuthRequestContext.class.getName() + ".sessionToken";
    static final String USER_ATTRIBUTE = AuthRequestContext.class.getName() + ".user";
    static final String REQUEST_ID_ATTRIBUTE = AuthRequestContext.class.getName() + ".requestId";

    private AuthRequestContext() {
    }

    static String sessionToken(HttpServletRequest request) {
        Object value = request.getAttribute(SESSION_TOKEN_ATTRIBUTE);
        if (value instanceof String token && !token.isBlank()) {
            return token;
        }
        throw new IllegalArgumentException("Session utilisateur requise.");
    }

    /**
     * Returns the {@link AuthUser} resolved by {@link ApiSecurityFilter} for this request,
     * or {@code null} if the route is public / unauthenticated. Safe to call from any
     * controller behind the filter without an extra database round-trip.
     */
    static AuthUser currentUserOrNull(HttpServletRequest request) {
        Object value = request.getAttribute(USER_ATTRIBUTE);
        return value instanceof AuthUser user ? user : null;
    }
}
