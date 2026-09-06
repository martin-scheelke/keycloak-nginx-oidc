package za.co.tsa.oidcdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the OIDC demo microservice.
 *
 * <p>The service is both an OAuth2 <em>client</em> (browser login via the authorization-code
 * flow against Keycloak) and an OAuth2 <em>resource server</em> (stateless {@code Bearer}
 * JWT access for API clients). It holds no database of its own.
 */
@SpringBootApplication
public class OidcDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(OidcDemoApplication.class, args);
    }
}
