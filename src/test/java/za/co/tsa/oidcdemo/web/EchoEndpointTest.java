package za.co.tsa.oidcdemo.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * HTTP-layer coverage of {@code POST /api/public/echo} per docs/design/public-echo.md, edge
 * cases 1-9. Exercises the real Bean Validation on the generated {@code EchoRequest} and the
 * real {@code PublicController} over HTTP via REST Assured (CLAUDE.md section 10), against a
 * real running server ({@code @SpringBootTest} + {@code RANDOM_PORT}) -- not MockMvc.
 *
 * <p>No Testcontainers / Keycloak: the endpoint is under {@code /api/public/**} with
 * {@code security: []} in the contract, so the {@code test} profile (explicit OAuth2 endpoints,
 * no discovery call) is enough to start the context without a real Keycloak, same as
 * {@link ControllerSecurityTest} and {@link OpenApiDocsTest}. Named {@code *Test} (not
 * {@code *IT}) so it runs under {@code mvn test} via surefire.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("POST /api/public/echo")
class EchoEndpointTest {

    private static final String PATH = "/api/public/echo";

    @LocalServerPort
    int port;

    @BeforeAll
    static void restAssuredConfig() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    void setPort() {
        RestAssured.port = port;
    }

    // ---- 1: message absent from body -----------------------------------------------------

    @Test
    void missingMessageFieldIsRejected() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
        .when()
                .post(PATH)
        .then()
                .statusCode(400)
                .body("status", equalTo(400));
    }

    // ---- 2: message: "" (blank) -----------------------------------------------------------

    @Test
    void blankMessageIsRejected() {
        given()
                .contentType(ContentType.JSON)
                .body(java.util.Map.of("message", ""))
        .when()
                .post(PATH)
        .then()
                .statusCode(400)
                .body("status", equalTo(400));
    }

    // ---- 3: message: "   " (whitespace-only: spaces/tabs/newlines) -----------------------

    @Test
    void whitespaceOnlyMessageIsRejected() {
        given()
                .contentType(ContentType.JSON)
                .body(java.util.Map.of("message", "   \t\n  "))
        .when()
                .post(PATH)
        .then()
                .statusCode(400)
                .body("status", equalTo(400));
    }

    // ---- 4: message exactly 200 characters (pre-trim) -> 200 ------------------------------

    @Test
    void exactlyTwoHundredCharacterMessageIsAccepted() {
        String message = "a".repeat(200);

        given()
                .contentType(ContentType.JSON)
                .body(java.util.Map.of("message", message))
        .when()
                .post(PATH)
        .then()
                .statusCode(200)
                .body("message", equalTo(message))
                .body("length", equalTo(200))
                .body("shout", equalTo("A".repeat(200)));
    }

    // ---- 5: message exactly 201 characters (pre-trim) -> 400 -------------------------------

    @Test
    void exactlyTwoHundredAndOneCharacterMessageIsRejected() {
        String message = "a".repeat(201);

        given()
                .contentType(ContentType.JSON)
                .body(java.util.Map.of("message", message))
        .when()
                .post(PATH)
        .then()
                .statusCode(400)
                .body("status", equalTo(400));
    }

    // ---- 6: Unicode happy path -------------------------------------------------------------

    @Test
    void unicodeMessageIsEchoedWithUtf16CodeUnitLength() {
        String message = "héllo wörld 日本語 🎉";

        given()
                .contentType(ContentType.JSON)
                .body(java.util.Map.of("message", message))
        .when()
                .post(PATH)
        .then()
                .statusCode(200)
                .body("message", equalTo(message))
                .body("length", equalTo(message.length()))
                .body("shout", equalTo(message.toUpperCase(java.util.Locale.ROOT)));
    }

    // ---- 7: already-uppercase happy path ----------------------------------------------------

    @Test
    void alreadyUppercaseMessageShoutEqualsTrimmedInput() {
        given()
                .contentType(ContentType.JSON)
                .body(java.util.Map.of("message", "ALREADY LOUD"))
        .when()
                .post(PATH)
        .then()
                .statusCode(200)
                .body("message", equalTo("ALREADY LOUD"))
                .body("length", equalTo(12))
                .body("shout", equalTo("ALREADY LOUD"));
    }

    // ---- 8: leading/trailing whitespace happy path ------------------------------------------

    @Test
    void leadingAndTrailingWhitespaceIsTrimmedBeforeLengthAndShout() {
        given()
                .contentType(ContentType.JSON)
                .body(java.util.Map.of("message", "  hi  "))
        .when()
                .post(PATH)
        .then()
                .statusCode(200)
                .body("message", equalTo("hi"))
                .body("length", equalTo(2))
                .body("shout", equalTo("HI"));
    }

    // ---- 9: no Authorization header required (endpoint is public) --------------------------

    @Test
    void noAuthorizationHeaderIsRequired() {
        given()
                .contentType(ContentType.JSON)
                .body(java.util.Map.of("message", "hello"))
        .when()
                .post(PATH)
        .then()
                .statusCode(200)
                .body("message", equalTo("hello"));
    }
}
