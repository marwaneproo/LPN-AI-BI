package com.lpn.aibi.llmorchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthRepository repository;

    private AuthPasswordService passwordService;
    private SessionTokenService tokenService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        AuthProperties properties = properties();
        passwordService = new AuthPasswordService(properties);
        tokenService = new SessionTokenService();
        service = new AuthService(repository, passwordService, tokenService, properties);
    }

    @Test
    void signUpNormalizesAndHashesTheIdentifierWithoutReturningAccountDetails() {
        when(repository.findByUsername("alice@lpn.ma")).thenReturn(Optional.empty());

        AuthService.AuthResponse response = service.signUp("  Alice@LPN.MA ", "Correct-Horse-42");

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.user()).isNull();
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).createPendingUser(org.mockito.ArgumentMatchers.eq("alice@lpn.ma"), hash.capture(), org.mockito.ArgumentMatchers.eq(""));
        assertThat(hash.getValue()).startsWith("$2");
    }

    @Test
    void signUpTreatsAConcurrentDuplicateAsTheSameAcceptedResult() {
        when(repository.findByUsername("alice")).thenReturn(Optional.empty());
        when(repository.createPendingUser(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        AuthService.AuthResponse response = service.signUp("alice", "Correct-Horse-42");

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.user()).isNull();
    }

    @Test
    void signUpRejectsWeakPasswords() {
        assertThatThrownBy(() -> service.signUp("alice", "short1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12");
    }

    @Test
    void loginCreatesAHashedServerSessionForAnApprovedUser() {
        AuthUser user = approvedUser();
        AuthPasswordService.PasswordSecret secret = passwordService.hash("Correct-Horse-42");
        when(repository.findByUsername("alice"))
                .thenReturn(Optional.of(new AuthRepository.AuthUserWithSecret(user, secret.hash(), secret.salt())));

        AuthService.LoginResult result = service.login("Alice", "Correct-Horse-42", true);

        assertThat(result.response().authenticated()).isTrue();
        assertThat(result.sessionToken()).hasSizeGreaterThan(40);
        assertThat(result.persistent()).isTrue();
        ArgumentCaptor<String> tokenHash = ArgumentCaptor.forClass(String.class);
        verify(repository).createSession(org.mockito.ArgumentMatchers.eq(user), tokenHash.capture(), any(Instant.class));
        assertThat(tokenHash.getValue()).hasSize(64).doesNotContain(result.sessionToken());
    }

    @Test
    void unknownAndInvalidCredentialsUseTheSamePublicResponse() {
        when(repository.findByUsername("missing")).thenReturn(Optional.empty());

        AuthService.LoginResult result = service.login("missing", "Wrong-password-42", false);

        assertThat(result.response().status()).isEqualTo("INVALID");
        assertThat(result.response().message()).isEqualTo("Identifiant ou mot de passe incorrect.");
        assertThat(result.sessionToken()).isNull();
    }

    @Test
    void legacyDefaultAdministratorCredentialIsNeverAccepted() {
        AuthUser admin = new AuthUser(
                UUID.randomUUID(), "admin", null, "ADMIN", "APPROVED", Instant.now(), Instant.now(), "system", null);
        AuthPasswordService.PasswordSecret secret = passwordService.hash("admin");
        when(repository.findByUsername("admin"))
                .thenReturn(Optional.of(new AuthRepository.AuthUserWithSecret(admin, secret.hash(), secret.salt())));

        AuthService.LoginResult result = service.login("admin", "admin", false);

        assertThat(result.response().status()).isEqualTo("INVALID");
        assertThat(result.sessionToken()).isNull();
    }

    @Test
    void logoutRevokesOnlyTheDigestOfThePresentedToken() {
        service.logout("raw-secret-session-token");

        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        verify(repository).revokeSession(digest.capture());
        assertThat(digest.getValue()).hasSize(64).doesNotContain("raw-secret-session-token");
    }

    @Test
    void bootstrapAllowsTheReservedAdminIdentifierOnlyThroughConfiguration() {
        service.bootstrapAdmin("admin", "A-strong-bootstrap-42");

        verify(repository).upsertAdmin(org.mockito.ArgumentMatchers.eq("admin"), anyString(), org.mockito.ArgumentMatchers.eq(""));
    }

    private AuthUser approvedUser() {
        return new AuthUser(UUID.randomUUID(), "alice", null, "USER", "APPROVED", Instant.now(), Instant.now(), "admin", null);
    }

    private AuthProperties properties() {
        return new AuthProperties(
                true,
                new AuthProperties.Cookie("lpn_session", false, "Strict"),
                new AuthProperties.Session(Duration.ofHours(12), Duration.ofDays(30)),
                new AuthProperties.Password(4),
                new AuthProperties.BootstrapAdmin("admin", ""),
                new AuthProperties.RateLimit(10, Duration.ofMinutes(15), 5, Duration.ofHours(1)));
    }
}
