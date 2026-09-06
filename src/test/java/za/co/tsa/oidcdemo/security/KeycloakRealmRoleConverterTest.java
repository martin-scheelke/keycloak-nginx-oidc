package za.co.tsa.oidcdemo.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("user-1");
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void mapsRealmRolesToPrefixedAuthorities() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", List.of("user", "admin"))));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_user", "ROLE_admin");
    }

    @Test
    void returnsEmptyWhenRealmAccessClaimMissing() {
        Jwt jwt = jwtWithClaims(Map.of("scope", "openid"));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void returnsEmptyWhenRolesEntryIsNotACollection() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", "not-a-list")));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void isCompatibleWithHasRoleSemantics() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", List.of("admin"))));

        assertThat(converter.convert(jwt))
                .containsExactlyElementsOf(AuthorityUtils.createAuthorityList("ROLE_admin"));
    }
}
