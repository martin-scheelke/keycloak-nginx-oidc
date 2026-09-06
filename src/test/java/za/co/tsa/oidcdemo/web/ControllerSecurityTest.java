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
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    // ---- anonymous ----------------------------------------------------------

    @Test
    void publicEndpointIsOpen() throws Exception {
        mvc.perform(get("/api/public/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello from a public endpoint"));
    }

    @Test
    void apiRequiresAuthenticationAndReturns401NotRedirect() throws Exception {
        mvc.perform(get("/api/user/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPageRedirectsToKeycloak() throws Exception {
        mvc.perform(get("/secured"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/oauth2/authorization/demo")));
    }

    // ---- Bearer JWT (resource server) -------------------------------------

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

    @Test
    void adminEndpointForbiddenWithoutAdminRole() throws Exception {
        mvc.perform(get("/api/admin/stats").with(jwt()
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointAllowedWithAdminRole() throws Exception {
        mvc.perform(get("/api/admin/stats").with(jwt()
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSessions").value(1));
    }

    // ---- browser session (OIDC login) -----------------------------------

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

    @Test
    void adminEndpointForbiddenForNonAdminBrowserSession() throws Exception {
        mvc.perform(get("/api/admin/stats").with(oidcLogin()
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isForbidden());
    }
}
