package com.lpn.aibi.llmorchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiSecurityFilterTest {

    private AuthService authService;
    private ApiSecurityFilter filter;

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
        filter = new ApiSecurityFilter(
                authService,
                new AuthSessionCookie(properties),
                properties,
                new ObjectMapper());
    }

    @Test
    void protectedApiRejectsRequestsWithoutASession() throws Exception {
        MockHttpServletResponse response = execute(new MockHttpServletRequest("GET", "/v1/qa"), new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_REQUIRED");
        assertThat(response.getHeader("X-Request-ID")).isNotBlank();
    }

    @Test
    void publicLoginRoutePassesWithoutASession() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/login");
        request.addHeader(ApiSecurityFilter.REQUEST_HEADER, "web");
        MockHttpServletResponse response = execute(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void authenticatedMutationRequiresTheSameOriginRequestHeader() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("POST", "/v1/qa");
        MockHttpServletResponse response = execute(request, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF_CHECK_FAILED");
    }

    @Test
    void authenticatedMutationPassesWithTheRequestHeader() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("POST", "/v1/qa");
        request.addHeader(ApiSecurityFilter.REQUEST_HEADER, "web");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = execute(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(chain.getRequest().getAttribute(AuthRequestContext.SESSION_TOKEN_ATTRIBUTE)).isEqualTo("raw-token");
    }

    private MockHttpServletRequest authenticatedRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setCookies(new Cookie("lpn_session", "raw-token"));
        when(authService.requireApprovedUser("raw-token")).thenReturn(new AuthUser(
                UUID.randomUUID(), "alice", null, "USER", "APPROVED", Instant.now(), Instant.now(), "admin", null));
        return request;
    }

    private MockHttpServletResponse execute(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
