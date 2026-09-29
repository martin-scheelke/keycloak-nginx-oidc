package za.co.tsa.oidcdemo.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the authorization rules of {@link za.co.tsa.oidcdemo.config.SecurityConfig} for both
 * authentication styles, using mocked credentials (no Keycloak).
 *
 * <p>These tests use MockMvc with Spring Security's test request post-processors ({@code jwt()},
 * {@code oidcLogin()}) to synthesize an already-authenticated request without a real token or a
 * running Keycloak — fast, but it means signature/issuer validation itself is never exercised
 * here. That's covered separately by {@link za.co.tsa.oidcdemo.integration.KeycloakContainerIT},
 * which obtains and sends real tokens from a real Keycloak container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    // ---- anonymous ----------------------------------------------------------

    /**
     * Calls the public endpoint with no credentials at all.
     *
     * <p>Verifies: an unauthenticated request to {@code /api/public/**} succeeds — the
     * baseline "this endpoint really is open" check that every other test in this class
     * implicitly contrasts with.
     */
    @Test
    void publicEndpointIsOpen() throws Exception {
        mvc.perform(get("/api/public/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello from a public endpoint"));
    }

    /**
     * Calls a protected API endpoint with no credentials.
     *
     * <p>Verifies: the response is a plain {@code 401 Unauthorized}, not a redirect. This
     * matters because Spring Security's default behavior for an unauthenticated request can
     * differ depending on how the security chain is configured — a REST/API client expects a
     * 401 it can handle programmatically, not a 302 to an HTML login page (that's the correct
     * behavior for {@code /secured} below, but would be wrong here).
     */
    @Test
    void apiRequiresAuthenticationAndReturns401NotRedirect() throws Exception {
        mvc.perform(get("/api/user/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Calls the browser-facing protected page with no credentials.
     *
     * <p>Verifies: the response is a redirect ({@code 3xx}) whose {@code Location} points at
     * this app's own {@code /oauth2/authorization/demo} endpoint (the entry point that starts
     * the OIDC authorization-code flow against the {@code demo} Keycloak client) — the
     * browser-appropriate counterpart to the 401-not-redirect behavior verified above for the
     * API path.
     */
    @Test
    void protectedPageRedirectsToKeycloak() throws Exception {
        mvc.perform(get("/secured"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/oauth2/authorization/demo")));
    }

    // ---- Bearer JWT (resource server) -------------------------------------

    /**
     * Simulates a resource-server request carrying a valid (mocked) Bearer JWT with the
     * {@code user} role.
     *
     * <p>Verifies: the request is accepted, and the response correctly reflects the mocked
     * JWT's claims/authorities — username from the {@code preferred_username} claim, roles
     * from the mocked authorities, and {@code authenticationMethod} reported as
     * {@code bearer-jwt} — proving {@link za.co.tsa.oidcdemo.security.AuthenticatedUserService}
     * is actually wired into the real request pipeline, not just correct in isolation (see
     * {@link za.co.tsa.oidcdemo.security.AuthenticatedUserServiceTest}).
     */
    @Test
    void userEndpointAcceptsAnyAuthenticatedJwt() throws Exception {
        mvc.perform(get("/api/user/me").with(jwt()
                        .jwt(j -> j.claim("preferred_username", "demo")
                                .claim("realm_access", Map.of("roles", java.util.List.of("user"))))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo"))
                .andExpect(jsonPath("$.authenticationMethod").value("bearer-jwt"))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("user")));
    }

    /**
     * Simulates a Bearer JWT request that is authenticated but only carries the {@code user}
     * role, hitting the admin-only endpoint.
     *
     * <p>Verifies: {@code 403 Forbidden} — being authenticated is not enough; the
     * {@code @PreAuthorize("hasRole('admin')")} check on the admin endpoint actually rejects a
     * caller missing that specific role. Paired with the next test, which proves the same
     * check lets the right role through.
     */
    @Test
    void adminEndpointForbiddenWithoutAdminRole() throws Exception {
        mvc.perform(get("/api/admin/stats").with(jwt()
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Simulates a Bearer JWT request carrying the {@code admin} role, hitting the admin-only
     * endpoint.
     *
     * <p>Verifies: {@code 200 OK} with the expected admin payload — the positive case showing
     * the role check isn't simply rejecting everything; a caller with the correct role is let
     * through and reaches the actual controller logic.
     */
    @Test
    void adminEndpointAllowedWithAdminRole() throws Exception {
        mvc.perform(get("/api/admin/stats").with(jwt()
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSessions").value(1));
    }

    // ---- browser session (OIDC login) -----------------------------------

    /**
     * Simulates a logged-in browser session (mocked {@code oidcLogin()}, not a Bearer token)
     * with the {@code user} role, calling the same {@code /api/user/me} endpoint as the JWT
     * test above.
     *
     * <p>Verifies: this second, entirely different authentication mechanism is also accepted
     * by the same endpoint, and correctly reports {@code authenticationMethod} as
     * {@code browser-session} rather than {@code bearer-jwt} — proving the endpoint really
     * does support "either a browser session or a Bearer JWT" as the API contract promises,
     * not just whichever one was tested first.
     */
    @Test
    void userEndpointAcceptsBrowserSession() throws Exception {
        mvc.perform(get("/api/user/me").with(oidcLogin()
                        .idToken(t -> t.claim("preferred_username", "alice")
                                .claim("realm_access", Map.of("roles", java.util.List.of("user"))))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.authenticationMethod").value("browser-session"));
    }

    /**
     * Simulates a logged-in browser session with only the {@code user} role, hitting the
     * admin-only endpoint.
     *
     * <p>Verifies: {@code 403 Forbidden} — the role-based authorization check is enforced
     * uniformly regardless of which authentication mechanism produced the caller's
     * authorities; it isn't a check that only happens to be wired up for the Bearer-JWT path
     * tested above ({@code adminEndpointForbiddenWithoutAdminRole}).
     */
    @Test
    void adminEndpointForbiddenForNonAdminBrowserSession() throws Exception {
        mvc.perform(get("/api/admin/stats").with(oidcLogin()
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isForbidden());
    }
}
