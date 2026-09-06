package za.co.tsa.oidcdemo.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

/**
 * Maps the authorities of a browser-authenticated {@code OidcUser} the same way
 * {@link KeycloakRealmRoleConverter} maps a resource-server JWT: Keycloak realm roles from
 * {@code realm_access.roles} (found in the ID token or the userinfo response) become
 * {@code ROLE_*} authorities, in addition to the default {@code SCOPE_*} / {@code OIDC_USER}
 * authorities Spring assigns.
 */
public class KeycloakOidcAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);
        for (GrantedAuthority authority : authorities) {
            Map<String, Object> claims = claimsFor(authority);
            if (claims != null) {
                mapped.addAll(realmRoles(claims));
            }
        }
        return mapped;
    }

    private static Map<String, Object> claimsFor(GrantedAuthority authority) {
        if (authority instanceof OidcUserAuthority oidc) {
            return oidc.getUserInfo() != null ? oidc.getUserInfo().getClaims() : oidc.getIdToken().getClaims();
        }
        if (authority instanceof OAuth2UserAuthority oauth2) {
            return oauth2.getAttributes();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Set<GrantedAuthority> realmRoles(Map<String, Object> claims) {
        Set<GrantedAuthority> roles = new LinkedHashSet<>();
        if (claims.get(REALM_ACCESS) instanceof Map<?, ?> realmAccess
                && realmAccess.get(ROLES) instanceof Collection<?> roleNames) {
            for (Object role : roleNames) {
                roles.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
            }
        }
        return roles;
    }
}
