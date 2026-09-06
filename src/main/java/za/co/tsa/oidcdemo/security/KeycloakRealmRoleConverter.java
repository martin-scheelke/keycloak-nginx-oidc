package za.co.tsa.oidcdemo.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extracts Keycloak <em>realm</em> roles from the {@code realm_access.roles} claim of a JWT
 * and exposes them as Spring Security authorities with the conventional {@code ROLE_} prefix,
 * so that {@code hasRole("admin")} / {@code @PreAuthorize("hasRole('admin')")} work.
 *
 * <p>Keycloak also emits client roles under {@code resource_access.<client>.roles}; this demo
 * only uses realm roles to keep the model simple.
 */
public final class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get(ROLES);
        if (!(roles instanceof Collection<?> roleNames)) {
            return List.of();
        }
        return roleNames.stream()
                .map(String::valueOf)
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
