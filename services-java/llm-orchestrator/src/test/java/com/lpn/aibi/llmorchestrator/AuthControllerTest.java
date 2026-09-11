package com.lpn.aibi.llmorchestrator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        AuthProperties properties = new AuthProperties(
                true,
                new AuthProperties.Cookie("lpn_session", false, "Strict"),
                new AuthProperties.Session(Duration.ofHours(12), Duration.ofDays(30)),
                new AuthProperties.Password(4),
                new AuthProperties.BootstrapAdmin("admin", ""),
                new AuthProperties.RateLimit(10, Duration.ofMinutes(15), 5, Duration.ofHours(1)));
        AuthController controller = new AuthController(
                authService,
                new AuthSessionCookie(properties),
                new AuthRateLimiter(),
                properties);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthApiExceptionHandler())
                .build();
    }

    @Test
    void successfulLoginSetsAnHttpOnlySameSiteCookieWithoutReturningTheToken() throws Exception {
        AuthService.AuthUserDto user = new AuthService.AuthUserDto(
                UUID.randomUUID(), "alice", "Alice Test", "USER", "Utilisateur", "APPROVED", null, null, null, null);
        when(authService.login("alice", "Correct-Horse-42", true)).thenReturn(new AuthService.LoginResult(
                new AuthService.AuthResponse(true, "APPROVED", user, "Connexion autorisée."),
                "raw-session-token",
                Duration.ofDays(30),
                true));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"Correct-Horse-42","rememberMe":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("lpn_session=raw-session-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void invalidCredentialsReturnUnauthorizedWithTheGenericMessage() throws Exception {
        when(authService.login("alice", "Wrong-password-42", false)).thenReturn(AuthService.LoginResult.failure(
                new AuthService.AuthResponse(false, "INVALID", null, "Identifiant ou mot de passe incorrect.")));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"Wrong-password-42","rememberMe":false}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Identifiant ou mot de passe incorrect."));
    }

    @Test
    void validSignupReturnsAcceptedAndShortPasswordsAreRejectedBeforeTheService() throws Exception {
        when(authService.signUp("alice", "Correct-Horse-42")).thenReturn(
                new AuthService.AuthResponse(false, "PENDING", null, "Demande prise en compte."));

        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"Correct-Horse-42"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"short1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
