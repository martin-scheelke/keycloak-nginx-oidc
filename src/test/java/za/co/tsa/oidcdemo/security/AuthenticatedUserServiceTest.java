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

/**
 * Unit tests for {@link AuthenticatedUserService}, which must produce a consistent
 * {@link UserInfoResponse} regardless of which of the two supported authentication styles
 * (Bearer JWT resource-server calls, or a browser OIDC session) actually authenticated the
 * caller. No Spring context: {@link Jwt}, {@link DefaultOidcUser} etc. are built by hand.
 */
class AuthenticatedUserServiceTest {

    private final AuthenticatedUserService service = new AuthenticatedUserService();

    /**
     * Builds a {@link JwtAuthenticationToken} (the type Spring Security uses for a validated
     * Bearer-token resource-server request) with realm roles and a space-separated
     * {@code scope} claim on the JWT itself.
     *
     * <p>Verifies: subject/username/email are read from the JWT's own claims, {@code roles}
     * comes from the {@code realm_access.roles} claim (not from the granted authorities),
     * {@code scopes} is the {@code scope} claim split on whitespace, and
     * {@code authenticationMethod} is reported as {@code BEARER_JWT} — pinning down exactly
     * which source each field is read from for this authentication style.
     */
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

    /**
     * Builds an {@code OAuth2AuthenticationToken} wrapping a {@link DefaultOidcUser} (the type
     * Spring Security uses for a logged-in browser session) whose claims and granted
     * authorities are set up independently of the JWT test above — the "user" role comes from
     * an ID token claim, but the {@code scope} values come from {@code SCOPE_*}-prefixed
     * granted authorities instead of a JWT claim, since a browser session has no raw JWT to
     * read a {@code scope} claim from.
     *
     * <p>Verifies: subject/username/roles are still read correctly from this different
     * principal type, {@code scopes} is correctly derived from the {@code SCOPE_*} authorities
     * (stripped of their prefix), and {@code authenticationMethod} is reported as
     * {@code BROWSER_SESSION} — proving the two authentication styles converge on the same
     * response shape via genuinely different code paths, not a shared assumption that happens
     * to work for one of them.
     */
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

    /**
     * Passes an {@link AnonymousAuthenticationToken} — the principal type Spring Security uses
     * for an unauthenticated request that reached this code anyway — which is neither of the
     * two types the service knows how to describe.
     *
     * <p>Verifies: the service fails loudly ({@code IllegalStateException}) rather than
     * quietly returning a mostly-empty or null-filled {@code UserInfoResponse} for a caller it
     * doesn't actually recognize as authenticated. This is a defensive guard — in normal
     * operation Spring Security's own filter chain should never let an anonymous request reach
     * this code for a protected endpoint, but if it ever did, failing fast here is safer than
     * fabricating a response.
     */
    @Test
    void rejectsUnsupportedAuthentication() {
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThatIllegalStateException().isThrownBy(() -> service.describe(anonymous));
    }
}
