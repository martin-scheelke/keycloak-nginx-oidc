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

    @Test
    void publicEndpointNeedsNoToken() {
        given().when().get("/api/public/hello")
                .then().statusCode(200).body("message", equalTo("Hello from a public endpoint"));
    }

    @Test
    void userEndpointRejectsMissingAndBogusTokens() {
        given().when().get("/api/user/me").then().statusCode(401);
        given().header("Authorization", "Bearer not-a-jwt").when().get("/api/user/me").then().statusCode(401);
    }

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
