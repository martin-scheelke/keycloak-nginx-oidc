package za.co.tsa.oidcdemo.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import za.co.tsa.oidcdemo.api.model.UserInfoResponse;
import za.co.tsa.oidcdemo.api.model.UserInfoResponse.AuthenticationMethodEnum;

/**
 * Builds a uniform {@link UserInfoResponse} (a model generated from {@code openapi/openapi.yaml})
 * from whichever authentication the request carried: a resource-server
 * {@link JwtAuthenticationToken} (API client) or a browser OIDC login (principal is an
 * {@link OidcUser}).
 */
@Service
public class AuthenticatedUserService {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String SCOPE_PREFIX = "SCOPE_";

    public UserInfoResponse describe(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return fromJwt(jwtAuth.getToken());
        }
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return fromOidcUser(oidcUser, authentication.getAuthorities());
        }
        throw new IllegalStateException("Unsupported authentication: "
                + (authentication == null ? "none" : authentication.getClass().getName()));
    }

    private UserInfoResponse fromJwt(Jwt jwt) {
        return new UserInfoResponse(
                jwt.getSubject(),
                realmRoles(jwt.getClaims()),
                scopes(jwt.getClaimAsString("scope")),
                AuthenticationMethodEnum.BEARER_JWT)
                .username(jwt.getClaimAsString("preferred_username"))
                .email(jwt.getClaimAsString("email"));
    }

    private UserInfoResponse fromOidcUser(OidcUser user, Collection<? extends GrantedAuthority> authorities) {
        return new UserInfoResponse(
                user.getSubject(),
                realmRoles(user.getClaims()),
                authoritiesWithPrefix(authorities, SCOPE_PREFIX),
                AuthenticationMethodEnum.BROWSER_SESSION)
                .username(user.getPreferredUsername())
                .email(user.getEmail());
    }

    @SuppressWarnings("unchecked")
    private List<String> realmRoles(Map<String, Object> claims) {
        if (claims.get(REALM_ACCESS) instanceof Map<?, ?> realmAccess
                && realmAccess.get(ROLES) instanceof Collection<?> roles) {
            return roles.stream().map(String::valueOf).sorted().toList();
        }
        return List.of();
    }

    private List<String> scopes(String scopeClaim) {
        if (scopeClaim == null || scopeClaim.isBlank()) {
            return List.of();
        }
        return List.of(scopeClaim.trim().split("\\s+"));
    }

    private List<String> authoritiesWithPrefix(Collection<? extends GrantedAuthority> authorities, String prefix) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(prefix))
                .map(a -> a.substring(prefix.length()))
                .sorted()
                .toList();
    }
}
