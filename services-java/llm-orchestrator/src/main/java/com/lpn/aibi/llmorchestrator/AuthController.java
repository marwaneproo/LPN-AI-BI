package com.lpn.aibi.llmorchestrator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
class AuthController {

    private static final String USERNAME_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._@-]*[A-Za-z0-9]$";

    private final AuthService authService;
    private final AuthSessionCookie sessionCookie;
    private final AuthRateLimiter rateLimiter;
    private final AuthProperties properties;

    AuthController(
            AuthService authService,
            AuthSessionCookie sessionCookie,
            AuthRateLimiter rateLimiter,
            AuthProperties properties) {
        this.authService = authService;
        this.sessionCookie = sessionCookie;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping("/login")
    ResponseEntity<AuthService.AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        String limitKey = "login:" + clientAddress(servletRequest);
        rateLimiter.check(limitKey, properties.rateLimit().loginAttempts(), properties.rateLimit().loginWindow());

        AuthService.LoginResult result = authService.login(request.username(), request.password(), request.rememberMe());
        if (result.response().authenticated()) {
            rateLimiter.reset(limitKey);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.SET_COOKIE,
                            sessionCookie.create(result.sessionToken(), result.ttl(), result.persistent()).toString())
                    .body(result.response());
        }

        HttpStatus status = result.response().status().equals("INVALID")
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(result.response());
    }

    @PostMapping("/signup")
    ResponseEntity<AuthService.AuthResponse> signUp(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.check(
                "signup:" + clientAddress(servletRequest),
                properties.rateLimit().signupAttempts(),
                properties.rateLimit().signupWindow());
        AuthService.AuthResponse response = authService.signUp(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/session")
    ResponseEntity<AuthService.AuthResponse> session(HttpServletRequest request) {
        var token = sessionCookie.read(request);
        if (token.isEmpty()) {
            return invalidSession();
        }
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(authService.currentSession(token.get()));
        } catch (IllegalArgumentException exception) {
            return invalidSession();
        }
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        sessionCookie.read(request).ifPresent(authService::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookie.clear().toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @GetMapping("/pending-users")
    List<AuthService.AuthUserDto> pendingUsers(HttpServletRequest request) {
        return authService.pendingUsers(AuthRequestContext.sessionToken(request));
    }

    @PatchMapping("/users/{id}/decision")
    AuthService.AuthUserDto decide(
            @PathVariable UUID id,
            @Valid @RequestBody UserDecisionRequest request,
            HttpServletRequest servletRequest) {
        return authService.decide(id, request.approved(), AuthRequestContext.sessionToken(servletRequest));
    }

    @GetMapping("/admin/users")
    List<AuthService.AuthUserDto> adminListUsers(
            @RequestParam(name = "q", required = false) String search, HttpServletRequest request) {
        return authService.adminListUsers(AuthRequestContext.sessionToken(request), search);
    }

    @PostMapping("/admin/users")
    ResponseEntity<AuthService.AuthUserDto> adminCreateUser(
            @Valid @RequestBody CreateUserRequest request, HttpServletRequest servletRequest) {
        AuthService.AuthUserDto created = authService.adminCreateUser(
                AuthRequestContext.sessionToken(servletRequest),
                request.fullName(),
                request.username(),
                request.password(),
                request.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/admin/users/{id}/role")
    AuthService.AuthUserDto adminUpdateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request,
            HttpServletRequest servletRequest) {
        return authService.adminUpdateRole(AuthRequestContext.sessionToken(servletRequest), id, request.role());
    }

    @PatchMapping("/admin/users/{id}/status")
    AuthService.AuthUserDto adminUpdateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request,
            HttpServletRequest servletRequest) {
        return authService.adminUpdateStatus(AuthRequestContext.sessionToken(servletRequest), id, request.enabled());
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/admin/users/{id}")
    ResponseEntity<Void> adminDeleteUser(@PathVariable UUID id, HttpServletRequest servletRequest) {
        authService.adminDeleteUser(AuthRequestContext.sessionToken(servletRequest), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/audit-log")
    List<AuthRepository.AuditLogEntry> adminAuditLog(
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit,
            HttpServletRequest request) {
        return authService.adminAuditLog(AuthRequestContext.sessionToken(request), limit);
    }

    @GetMapping("/admin/qa-audit")
    List<AuthRepository.QaAuditEntry> adminQaAudit(
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit,
            HttpServletRequest request) {
        return authService.adminQaAudit(AuthRequestContext.sessionToken(request), limit);
    }

    @GetMapping("/admin/roles")
    java.util.Map<String, String> adminRoles() {
        return AuthService.ROLE_LABELS;
    }

    private String clientAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null ? "unknown" : remoteAddress.toLowerCase(Locale.ROOT);
    }

    private ResponseEntity<AuthService.AuthResponse> invalidSession() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, sessionCookie.clear().toString())
                .cacheControl(CacheControl.noStore())
                .body(new AuthService.AuthResponse(false, "INVALID", null, "Session utilisateur requise."));
    }

    record LoginRequest(
            @NotBlank @Size(min = 3, max = 254) @Pattern(regexp = USERNAME_PATTERN) String username,
            @NotBlank @Size(max = 128) String password,
            boolean rememberMe) {
    }

    record SignupRequest(
            @NotBlank @Size(min = 3, max = 254) @Pattern(regexp = USERNAME_PATTERN) String username,
            @NotBlank @Size(min = 12, max = 128) String password) {
    }

    record UserDecisionRequest(boolean approved) {
    }

    record CreateUserRequest(
            @NotBlank @Size(min = 2, max = 200) String fullName,
            @NotBlank @Size(min = 3, max = 254) @Pattern(regexp = USERNAME_PATTERN) String username,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank String role) {
    }

    record UpdateRoleRequest(@NotBlank String role) {
    }

    record UpdateStatusRequest(boolean enabled) {
    }
}
