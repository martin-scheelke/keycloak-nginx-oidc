package za.co.tsa.oidcdemo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import za.co.tsa.oidcdemo.api.model.UserInfoResponse;
import za.co.tsa.oidcdemo.api.model.UserInfoResponse.AuthenticationMethodEnum;

class AuthenticatedUserServiceTest {

    private final AuthenticatedUserService service = new AuthenticatedUserService();

    @Test
    void describesResourceServerJwtAuthentication() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .subject("sub-123")
                .claim("preferred_username", "demo")
                .claim("email", "demo@example.com")
                .claim("scope", "openid profile email")
                .claim("realm_access", Map.of("roles", List.of("admin", "user")))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt,
                AuthorityUtils.createAuthorityList("ROLE_admin", "ROLE_user"));

        UserInfoResponse info = service.describe(auth);

        assertThat(info.getSubject()).isEqualTo("sub-123");
        assertThat(info.getUsername()).isEqualTo("demo");
        assertThat(info.getEmail()).isEqualTo("demo@example.com");
        assertThat(info.getRoles()).containsExactly("admin", "user");
        assertThat(info.getScopes()).containsExactly("openid", "profile", "email");
        assertThat(info.getAuthenticationMethod()).isEqualTo(AuthenticationMethodEnum.BEARER_JWT);
    }

    @Test
    void describesBrowserOidcAuthentication() {
        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of(
                        "sub", "sub-456",
                        "preferred_username", "alice",
                        "email", "alice@example.com",
                        "realm_access", Map.of("roles", List.of("user"))));
        DefaultOidcUser principal = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_user", "SCOPE_openid", "SCOPE_email"),
                idToken, "preferred_username");
        var auth = new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), "demo");

        UserInfoResponse info = service.describe(auth);

        assertThat(info.getSubject()).isEqualTo("sub-456");
        assertThat(info.getUsername()).isEqualTo("alice");
        assertThat(info.getRoles()).containsExactly("user");
        assertThat(info.getScopes()).containsExactly("email", "openid");
        assertThat(info.getAuthenticationMethod()).isEqualTo(AuthenticationMethodEnum.BROWSER_SESSION);
    }

    @Test
    void rejectsUnsupportedAuthentication() {
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThatIllegalStateException().isThrownBy(() -> service.describe(anonymous));
    }
}
