package za.co.tsa.oidcdemo.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit tests for {@link KeycloakRealmRoleConverter}, which turns Keycloak's non-standard
 * {@code realm_access.roles} JWT claim into Spring Security {@link GrantedAuthority} objects.
 * Spring's {@code hasRole()} family of checks (e.g. {@code @PreAuthorize("hasRole('admin')")})
 * only works if authorities are prefixed {@code ROLE_}, so getting that prefix — and handling
 * a missing or malformed claim without throwing — right here is load-bearing for every
 * role-gated endpoint in the app.
 */
class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    /** Builds a minimal but otherwise-valid JWT carrying the given extra claims, so each test
     * below only has to specify the one claim it actually cares about. */
    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("user-1");
        claims.forEach(builder::claim);
        return builder.build();
    }

    /**
     * Feeds a JWT with a well-formed {@code realm_access.roles} claim listing two roles.
     *
     * <p>Verifies: the converter's core job — each role string is turned into a
     * {@code GrantedAuthority} named {@code ROLE_<role>} (order-independent, since Keycloak
     * doesn't guarantee role ordering).
     */
    @Test
    void mapsRealmRolesToPrefixedAuthorities() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", List.of("user", "admin"))));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_user", "ROLE_admin");
    }

    /**
     * Feeds a JWT that has some other claim ({@code scope}) but no {@code realm_access} claim
     * at all — e.g. a token from a differently-configured client, or one that simply has no
     * realm roles assigned.
     *
     * <p>Verifies: the converter returns an empty authority list rather than throwing (e.g. a
     * {@code NullPointerException} from blindly navigating into a missing claim) — a token
     * without realm roles should just mean "no role-based authorities", not a hard failure.
     */
    @Test
    void returnsEmptyWhenRealmAccessClaimMissing() {
        Jwt jwt = jwtWithClaims(Map.of("scope", "openid"));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    /**
     * Feeds a JWT where {@code realm_access.roles} is present but is a plain string instead of
     * a list — a malformed/unexpected shape that shouldn't occur from real Keycloak but is
     * cheap to defend against.
     *
     * <p>Verifies: the converter returns an empty authority list instead of throwing a
     * {@code ClassCastException} when the claim isn't the collection type it expects — the
     * converter degrades safely on unexpected token shapes rather than breaking authentication
     * entirely for that request.
     */
    @Test
    void returnsEmptyWhenRolesEntryIsNotACollection() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", "not-a-list")));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    /**
     * Feeds a JWT with a single realm role and compares the converter's output directly
     * against {@link AuthorityUtils#createAuthorityList}, the standard Spring Security helper
     * for building {@code ROLE_}-prefixed authorities.
     *
     * <p>Verifies: the converter's output isn't just "close enough" but is byte-for-byte what
     * Spring Security's own {@code hasRole()} machinery expects — this is the test that would
     * catch a subtle prefix or casing mismatch (e.g. {@code "Role_admin"} vs {@code
     * "ROLE_admin"}) that the other tests, which only check the role name, wouldn't notice.
     */
    @Test
    void isCompatibleWithHasRoleSemantics() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", List.of("admin"))));

        assertThat(converter.convert(jwt))
                .containsExactlyElementsOf(AuthorityUtils.createAuthorityList("ROLE_admin"));
    }
}
