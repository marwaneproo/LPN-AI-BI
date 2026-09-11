package com.lpn.aibi.llmorchestrator;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {

    private static final int MINIMUM_PASSWORD_LENGTH = 12;
    private static final int MAXIMUM_PASSWORD_LENGTH = 128;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._@-]*[a-z0-9]$");
    private static final Set<String> BLOCKED_PASSWORDS = Set.of(
            "admin12345678",
            "password1234",
            "motdepasse123",
            "changeme12345");
    private static final String SIGNUP_ACCEPTED_MESSAGE =
            "Si votre demande est admissible, elle apparaîtra bientôt dans la file d'approbation.";

    // The seven LPN business roles. "USER" is kept only for accounts created by the
    // legacy self-service signup flow before this role model existed; it is never
    // offered as a choice in the admin console.
    static final Set<String> ASSIGNABLE_ROLES =
            Set.of("ADMIN", "DG", "ACHATS", "COMMERCIAL", "LOGISTIQUE", "DAF", "CLIENT_B2B");

    static final java.util.Map<String, String> ROLE_LABELS = java.util.Map.ofEntries(
            java.util.Map.entry("ADMIN", "Administrateur"),
            java.util.Map.entry("DG", "Directeur Général"),
            java.util.Map.entry("ACHATS", "Responsable Achats"),
            java.util.Map.entry("COMMERCIAL", "Service Commercial"),
            java.util.Map.entry("LOGISTIQUE", "Service Logistique"),
            java.util.Map.entry("DAF", "Direction Admin. & Financière"),
            java.util.Map.entry("CLIENT_B2B", "Client B2B"),
            java.util.Map.entry("USER", "Utilisateur"));

    private final AuthRepository authRepository;
    private final AuthPasswordService passwordService;
    private final SessionTokenService sessionTokenService;
    private final AuthProperties properties;

    AuthService(
            AuthRepository authRepository,
            AuthPasswordService passwordService,
            SessionTokenService sessionTokenService,
            AuthProperties properties) {
        this.authRepository = authRepository;
        this.passwordService = passwordService;
        this.sessionTokenService = sessionTokenService;
        this.properties = properties;
    }

    @Transactional
    LoginResult login(String username, String password, boolean rememberMe) {
        String normalizedUsername = normalizeUsername(username);
        Optional<AuthRepository.AuthUserWithSecret> stored = authRepository.findByUsername(normalizedUsername);
        if (stored.isEmpty()) {
            passwordService.consumeDummyHash(password);
            authRepository.logAuditEvent(normalizedUsername, "Connexion refusée", "Identifiant inconnu", "ERREUR");
            return LoginResult.failure(invalidCredentials());
        }

        AuthRepository.AuthUserWithSecret account = stored.get();
        if (!passwordService.matches(password, account.passwordHash(), account.passwordSalt())) {
            authRepository.logAuditEvent(normalizedUsername, "Connexion refusée", "Mot de passe incorrect", "ERREUR");
            return LoginResult.failure(invalidCredentials());
        }

        AuthUser user = account.user();
        if (user.role().equals("ADMIN") && "admin".equals(password)) {
            authRepository.logAuditEvent(normalizedUsername, "Connexion refusée", "Mot de passe par défaut refusé", "ERREUR");
            return LoginResult.failure(invalidCredentials());
        }
        if (!user.status().equals("APPROVED")) {
            authRepository.logAuditEvent(normalizedUsername, "Connexion refusée", "Statut : " + user.status(), "ERREUR");
            return LoginResult.failure(new AuthResponse(false, user.status(), null, statusMessage(user.status())));
        }

        if (passwordService.needsUpgrade(account.passwordHash())) {
            AuthPasswordService.PasswordSecret upgraded = passwordService.hash(password);
            authRepository.updatePasswordSecret(user.id(), upgraded.hash(), upgraded.salt());
        }

        String rawToken = sessionTokenService.generate();
        var ttl = rememberMe ? properties.session().rememberedTtl() : properties.session().standardTtl();
        Instant expiresAt = Instant.now().plus(ttl);
        authRepository.createSession(user, sessionTokenService.digest(rawToken), expiresAt);
        authRepository.touchLastLogin(user.id());
        authRepository.logAuditEvent(
                user.username(), "Connexion réussie", "Rôle : " + roleLabel(user.role()), "OK");
        AuthResponse response = new AuthResponse(true, "APPROVED", toDto(user), "Connexion autorisée.");
        return new LoginResult(response, rawToken, ttl, rememberMe);
    }

    @Transactional
    AuthResponse signUp(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        validateUsername(normalizedUsername, false);
        validateNewPassword(normalizedUsername, password);

        AuthPasswordService.PasswordSecret secret = passwordService.hash(password);
        if (authRepository.findByUsername(normalizedUsername).isPresent()) {
            return signupAccepted();
        }

        // ON CONFLICT DO NOTHING closes concurrent registration races without leaking account existence.
        authRepository.createPendingUser(normalizedUsername, secret.hash(), secret.salt());
        return signupAccepted();
    }

    AuthResponse currentSession(String token) {
        AuthUser user = requireApprovedUser(token);
        return new AuthResponse(true, "APPROVED", toDto(user), "Session active.");
    }

    void logout(String token) {
        if (token != null && !token.isBlank()) {
            authRepository.findBySessionTokenHash(sessionTokenService.digest(token)).ifPresent(user ->
                    authRepository.logAuditEvent(user.username(), "Déconnexion", "Session terminée par l'utilisateur", "OK"));
            authRepository.revokeSession(sessionTokenService.digest(token));
        }
    }

    List<AuthUserDto> pendingUsers(String adminToken) {
        requireAdmin(adminToken);
        return authRepository.pendingUsers().stream().map(this::toDto).toList();
    }

    @Transactional
    AuthUserDto decide(UUID userId, boolean approved, String adminToken) {
        AuthUser admin = requireAdmin(adminToken);
        return authRepository.decide(userId, approved, admin.username())
                .map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
    }

    AuthUser requireApprovedUser(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Session utilisateur requise.");
        }
        return authRepository.findBySessionTokenHash(sessionTokenService.digest(token))
                .orElseThrow(() -> new IllegalArgumentException("Session utilisateur requise."));
    }

    @Transactional
    void bootstrapAdmin(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        validateUsername(normalizedUsername, true);
        validateNewPassword(normalizedUsername, password);
        AuthPasswordService.PasswordSecret secret = passwordService.hash(password);
        authRepository.upsertAdmin(normalizedUsername, secret.hash(), secret.salt());
    }

    // ---------------------------------------------------------------------
    // Admin console: user provisioning, role & status management, audit log
    // ---------------------------------------------------------------------

    List<AuthUserDto> adminListUsers(String adminToken, String search) {
        requireAdmin(adminToken);
        return authRepository.listUsers(search).stream().map(this::toDto).toList();
    }

    @Transactional
    AuthUserDto adminCreateUser(String adminToken, String fullName, String username, String password, String role) {
        AuthUser admin = requireAdmin(adminToken);
        String normalizedUsername = normalizeUsername(username);
        validateUsername(normalizedUsername, true);
        validateNewPassword(normalizedUsername, password);
        validateAssignableRole(role);
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Le nom complet est obligatoire.");
        }
        if (authRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("Cet identifiant est déjà utilisé.");
        }

        AuthPasswordService.PasswordSecret secret = passwordService.hash(password);
        AuthUser created = authRepository
                .createUserByAdmin(normalizedUsername, fullName.strip(), secret.hash(), secret.salt(), role, admin.username())
                .orElseThrow(() -> new IllegalArgumentException("Cet identifiant est déjà utilisé."));
        authRepository.logAuditEvent(
                admin.username(),
                "Création de compte",
                created.username() + " · Rôle : " + roleLabel(role),
                "OK");
        return toDto(created);
    }

    @Transactional
    AuthUserDto adminUpdateRole(String adminToken, UUID userId, String role) {
        AuthUser admin = requireAdmin(adminToken);
        validateAssignableRole(role);
        AuthUser updated = authRepository.updateRole(userId, role)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Utilisateur introuvable ou modification non autorisée pour ce compte."));
        authRepository.logAuditEvent(
                admin.username(),
                "Changement de rôle",
                updated.username() + " → " + roleLabel(role),
                "OK");
        return toDto(updated);
    }

    @Transactional
    AuthUserDto adminUpdateStatus(String adminToken, UUID userId, boolean enabled) {
        AuthUser admin = requireAdmin(adminToken);
        AuthUser updated = authRepository.updateStatus(userId, enabled ? "APPROVED" : "DISABLED")
                .orElseThrow(() -> new IllegalArgumentException(
                        "Utilisateur introuvable ou modification non autorisée pour ce compte."));
        authRepository.logAuditEvent(
                admin.username(),
                enabled ? "Réactivation de compte" : "Désactivation de compte",
                updated.username(),
                "OK");
        return toDto(updated);
    }

    @Transactional
    void adminDeleteUser(String adminToken, UUID userId) {
        AuthUser admin = requireAdmin(adminToken);
        if (admin.id().equals(userId)) {
            throw new IllegalArgumentException("Vous ne pouvez pas supprimer votre propre compte administrateur.");
        }
        boolean deleted = authRepository.deleteUser(userId);
        if (!deleted) {
            throw new IllegalArgumentException(
                    "Utilisateur introuvable ou suppression non autorisée pour le compte administrateur principal.");
        }
        authRepository.logAuditEvent(admin.username(), "Suppression de compte", userId.toString(), "OK");
    }

    List<AuthRepository.AuditLogEntry> adminAuditLog(String adminToken, int limit) {
        requireAdmin(adminToken);
        return authRepository.recentAuditLog(Math.min(Math.max(limit, 1), 200));
    }

    List<AuthRepository.QaAuditEntry> adminQaAudit(String adminToken, int limit) {
        requireAdmin(adminToken);
        return authRepository.recentQaAudit(Math.min(Math.max(limit, 1), 200));
    }

    static String roleLabel(String role) {
        return ROLE_LABELS.getOrDefault(role, role);
    }

    private void validateAssignableRole(String role) {
        if (role == null || !ASSIGNABLE_ROLES.contains(role)) {
            throw new IllegalArgumentException("Rôle invalide.");
        }
    }

    static String normalizeUsername(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    private AuthUser requireAdmin(String token) {
        AuthUser user = requireApprovedUser(token);
        if (!user.role().equals("ADMIN")) {
            throw new AuthForbiddenException("Session administrateur requise.");
        }
        return user;
    }

    private void validateUsername(String username, boolean allowReservedAdmin) {
        if (username.length() < 3 || username.length() > 254 || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "L'identifiant doit contenir 3 à 254 caractères autorisés (lettres, chiffres, point, tiret, @ ou _)."
            );
        }
        if (!allowReservedAdmin && username.equals("admin")) {
            throw new IllegalArgumentException("Cet identifiant est réservé.");
        }
    }

    private void validateNewPassword(String username, String password) {
        if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH || password.length() > MAXIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Le mot de passe doit contenir entre 12 et 128 caractères.");
        }
        String normalizedPassword = password.toLowerCase(Locale.ROOT);
        if (!password.chars().anyMatch(Character::isLetter) || !password.chars().anyMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins une lettre et un chiffre.");
        }
        if (BLOCKED_PASSWORDS.contains(normalizedPassword) || normalizedPassword.contains(username)) {
            throw new IllegalArgumentException("Choisissez un mot de passe qui ne contient pas votre identifiant.");
        }
    }

    private AuthResponse invalidCredentials() {
        return new AuthResponse(false, "INVALID", null, "Identifiant ou mot de passe incorrect.");
    }

    private AuthResponse signupAccepted() {
        return new AuthResponse(false, "PENDING", null, SIGNUP_ACCEPTED_MESSAGE);
    }

    private AuthUserDto toDto(AuthUser user) {
        return new AuthUserDto(
                user.id(),
                user.username(),
                user.fullName(),
                user.role(),
                roleLabel(user.role()),
                user.status(),
                user.createdAt() == null ? null : user.createdAt().toString(),
                user.decidedAt() == null ? null : user.decidedAt().toString(),
                user.decidedBy(),
                user.lastLoginAt() == null ? null : user.lastLoginAt().toString());
    }

    private String statusMessage(String status) {
        return switch (status) {
            case "PENDING" -> "Votre demande est en attente d'approbation.";
            case "REJECTED" -> "Connexion non autorisée. Contactez votre administrateur.";
            case "DISABLED" -> "Ce compte a été désactivé. Contactez votre administrateur.";
            default -> "Connexion non autorisée.";
        };
    }

    record AuthResponse(boolean authenticated, String status, AuthUserDto user, String message) {
    }

    record AuthUserDto(
            UUID id,
            String username,
            String fullName,
            String role,
            String roleLabel,
            String status,
            String createdAt,
            String decidedAt,
            String decidedBy,
            String lastLoginAt) {
    }

    record LoginResult(AuthResponse response, String sessionToken, java.time.Duration ttl, boolean persistent) {
        static LoginResult failure(AuthResponse response) {
            return new LoginResult(response, null, null, false);
        }
    }
}
