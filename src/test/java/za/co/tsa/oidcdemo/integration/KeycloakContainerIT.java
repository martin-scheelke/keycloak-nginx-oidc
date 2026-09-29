package za.co.tsa.oidcdemo.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full resource-server slice against a <em>real</em> Keycloak (Testcontainers) using the same
 * realm import that docker-compose uses. Tokens are obtained via the direct-access-grant and
 * exercised over HTTP with REST Assured.
 *
 * <p>This is the layer that verifies what {@link za.co.tsa.oidcdemo.web.ControllerSecurityTest}
 * structurally cannot: that a real Keycloak-issued, real-signature JWT is actually accepted,
 * and that a token that merely looks like a JWT but wasn't issued by this issuer is rejected.
 * The imported realm ({@code compose/keycloak/realm-demo.json}) seeds two users this class
 * relies on: {@code demo} (roles: {@code user}, {@code admin}) and {@code alice} (role:
 * {@code user} only) — the same two users the docker-compose stack uses for manual testing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Resource server against real Keycloak")
class KeycloakContainerIT {

    private static final String REALM = "demo";
    private static final String CLIENT_ID = "demo-app";
    private static final String CLIENT_SECRET = "demo-app-secret";

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.1")
            .withRealmImportFile("compose/keycloak/realm-demo.json");

    @LocalServerPort
    int port;

    /** Points the app's OAuth2 resource-server/client config at this test's own Testcontainers
     * Keycloak instance instead of whatever's in application.yml, since the container's URL
     * (and thus its issuer) is only known once it has actually started. */
    @DynamicPropertySource
    static void oidcProperties(DynamicPropertyRegistry registry) {
        String issuer = KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM;
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuer);
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", () -> issuer);
        registry.add("spring.security.oauth2.client.registration.demo.client-id", () -> CLIENT_ID);
        registry.add("spring.security.oauth2.client.registration.demo.client-secret", () -> CLIENT_SECRET);
    }

    @BeforeAll
    static void restAssuredConfig() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    void setPort() {
        RestAssured.port = port;
    }

    /**
     * Performs a real OAuth2 Resource Owner Password Credentials grant against the
     * Testcontainers Keycloak for the given seeded user, and returns the resulting real,
     * genuinely-signed access token. Used by the tests below instead of a hand-built/mocked
     * JWT, which is the whole point of this test class.
     */
    private String accessToken(String username, String password) {
        return given()
                .contentType(ContentType.URLENC)
                .formParam("grant_type", "password")
                .formParam("client_id", CLIENT_ID)
                .formParam("client_secret", CLIENT_SECRET)
                .formParam("username", username)
                .formParam("password", password)
                .formParam("scope", "openid profile email")
            .when()
                .post(KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/token")
            .then()
                .statusCode(200)
                .extract().path("access_token");
    }

    /**
     * Calls the public endpoint with no token, against the real running application (not
     * MockMvc).
     *
     * <p>Verifies: the public endpoint stays open even once a real OAuth2 resource-server
     * configuration (real issuer, real JWK set) is active — i.e. wiring up real Keycloak
     * validation didn't accidentally start requiring auth everywhere.
     */
    @Test
    void publicEndpointNeedsNoToken() {
        given().when().get("/api/public/hello")
                .then().statusCode(200).body("message", equalTo("Hello from a public endpoint"));
    }

    /**
     * Calls a protected endpoint twice: once with no {@code Authorization} header at all, and
     * once with a header carrying an obviously-fake token ({@code "not-a-jwt"}).
     *
     * <p>Verifies both {@code 401}: the "no token" case is the same thing
     * {@link za.co.tsa.oidcdemo.web.ControllerSecurityTest} already checks with a mock, but the
     * "garbage token" case is something only this real-Keycloak class can actually verify —
     * that Spring Security's resource-server filter really does validate a token's signature
     * and structure against the real issuer's JWK set, rather than that check being mocked
     * away or accidentally bypassed.
     */
    @Test
    void userEndpointRejectsMissingAndBogusTokens() {
        given().when().get("/api/user/me").then().statusCode(401);
        given().header("Authorization", "Bearer not-a-jwt").when().get("/api/user/me").then().statusCode(401);
    }

    /**
     * Obtains a real token for the seeded {@code demo} user (who has both the {@code user} and
     * {@code admin} realm roles), then calls both the plain user endpoint and the admin-only
     * endpoint with it.
     *
     * <p>Verifies end-to-end, with no mocking anywhere in the chain: the real token is
     * accepted, {@code /api/user/me} reports the real subject/username/email/roles/scopes
     * pulled from that genuine token, and {@code /api/admin/stats} — gated by
     * {@code @PreAuthorize("hasRole('admin')")} — is reachable because the real
     * {@code realm_access.roles} claim on this token really does include {@code admin}. This
     * is the closest thing in the suite to "does a real user's real login actually work".
     */
    @Test
    void demoUserSeesBothRealmRolesAndReachesAdminEndpoint() {
        String token = accessToken("demo", "demo");

        given().header("Authorization", "Bearer " + token)
            .when().get("/api/user/me")
            .then().statusCode(200)
                .body("subject", not(emptyOrNullString()))
                .body("username", equalTo("demo"))
                .body("email", equalTo("demo@example.com"))
                .body("authenticationMethod", equalTo("bearer-jwt"))
                .body("roles", containsInAnyOrder("user", "admin"))
                .body("scopes", hasItem("openid"));

        given().header("Authorization", "Bearer " + token)
            .when().get("/api/admin/stats")
            .then().statusCode(200).body("activeSessions", equalTo(1));
    }

    /**
     * Obtains a real token for the seeded {@code alice} user (who has only the {@code user}
     * role), then calls both endpoints with it.
     *
     * <p>Verifies: {@code /api/user/me} succeeds and correctly reports just the {@code user}
     * role (not {@code admin} — proving the previous test's {@code admin} role for
     * {@code demo} isn't just always granted to everyone), while {@code /api/admin/stats}
     * correctly returns {@code 403} for this real, authenticated-but-under-privileged user.
     * The real-token counterpart to
     * {@link za.co.tsa.oidcdemo.web.ControllerSecurityTest#adminEndpointForbiddenWithoutAdminRole}.
     */
    @Test
    void aliceIsAuthenticatedButForbiddenFromAdminEndpoint() {
        String token = accessToken("alice", "alice");

        given().header("Authorization", "Bearer " + token)
            .when().get("/api/user/me")
            .then().statusCode(200).body("roles", containsInAnyOrder("user"));

        given().header("Authorization", "Bearer " + token)
            .when().get("/api/admin/stats")
            .then().statusCode(403);
    }
}
