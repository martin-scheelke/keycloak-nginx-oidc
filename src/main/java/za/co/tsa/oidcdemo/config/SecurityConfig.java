package za.co.tsa.oidcdemo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import za.co.tsa.oidcdemo.security.KeycloakOidcAuthoritiesMapper;
import za.co.tsa.oidcdemo.security.KeycloakRealmRoleConverter;

/**
 * Single filter chain that supports two authentication styles against the same Keycloak realm:
 *
 * <ul>
 *   <li><b>Browser</b> &mdash; {@code oauth2Login}: an unauthenticated request to a protected
 *       page is redirected to Keycloak, the user signs in, and a session is established.</li>
 *   <li><b>API client</b> &mdash; {@code oauth2ResourceServer}: a request carrying
 *       {@code Authorization: Bearer <access-token>} is validated statelessly against the
 *       realm's JWKS.</li>
 * </ul>
 *
 * <p>When a request has no {@code Authorization: Bearer} header it is treated as a browser
 * request (redirect to login); when it does, a failure returns {@code 401} rather than a
 * redirect.
 *
 * <p><b>Demo simplification:</b> CSRF protection is disabled so the static landing page and
 * {@code curl}-based API calls need no token plumbing. In production keep CSRF enabled for the
 * session-based part (e.g. {@code CookieCsrfTokenRepository.withHttpOnlyFalse()}) and only
 * exempt the stateless {@code /api/**} resource-server routes.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    public SecurityConfig(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher apiRequests = new AntPathRequestMatcher("/api/**");
        RequestMatcher browserRequests = new NegatedRequestMatcher(apiRequests);

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/index.html", "/favicon.ico", "/css/**", "/js/**", "/error")
                .permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/openapi.yaml", "/swagger-ui.html", "/webjars/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("admin")
                .requestMatchers("/api/user/**").authenticated()
                .anyRequest().authenticated())
            .oauth2Login(Customizer.withDefaults())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
            // API requests that fail auth get 401; browser requests get redirected to Keycloak.
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), apiRequests)
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/demo"), browserRequests))
            .logout(logout -> logout
                .logoutSuccessHandler(oidcLogoutSuccessHandler())
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID"))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    /** Realm-role mapping for the browser ({@code OidcUser}) authentication path. */
    @Bean
    public GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return new KeycloakOidcAuthoritiesMapper();
    }

    /** Realm-role mapping for the {@code Bearer} JWT (resource-server) authentication path. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        KeycloakRealmRoleConverter realmRoles = new KeycloakRealmRoleConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(realmRoles::convert);
        return converter;
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }
}
