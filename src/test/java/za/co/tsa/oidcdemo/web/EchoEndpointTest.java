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
 *
 * <p>Every {@code 400} assertion below checks only {@code status == 400}, never a
 * {@code message}/{@code errors} field — this project has no {@code @ControllerAdvice}, so a
 * failed {@code @Valid} falls through to Spring Boot's bare default error body
 * ({@code {timestamp, status, error, path}}); asserting on a field that doesn't exist would
 * make these tests pass for the wrong reason if that default body shape ever changed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("POST /api/public/echo")
class EchoEndpointTest {

    private static final String PATH = "/api/public/echo";

    @LocalServerPort
    int port;

    /** Makes REST Assured print the full request/response when an assertion fails, so a
     * failing test shows exactly what was sent and what came back instead of just "expected
     * X, got Y". Runs once for the whole class. */
    @BeforeAll
    static void restAssuredConfig() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    /** Points REST Assured at the random port Spring Boot picked for this test run (a fresh
     * port every run, since the real port is not known until the embedded server starts). */
    @BeforeEach
    void setPort() {
        RestAssured.port = port;
    }

    // ---- 1: message absent from body -----------------------------------------------------

    /**
     * Posts an empty JSON object {@code {}} — the {@code message} field is missing entirely,
     * not just empty.
     *
     * <p>Verifies: the generated {@code @NotNull} constraint on {@code EchoRequest.message}
     * rejects a missing field with {@code 400}, before the request ever reaches
     * {@code PublicController}.
     */
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

    /**
     * Posts {@code {"message": ""}} — the field is present but an empty string.
     *
     * <p>Verifies: the empty string is rejected with {@code 400}. In this contract that's
     * actually enforced by the {@code pattern} constraint (which requires at least one
     * non-whitespace character), not {@code minLength} alone — this test doesn't care which
     * constraint fires, only that the endpoint correctly refuses an empty message.
     */
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

    /**
     * Posts a message made entirely of spaces, a tab, and a newline — technically non-empty
     * (so {@code minLength: 1} alone would let it through), but with no actual content.
     *
     * <p>Verifies: this is the specific case the schema's {@code pattern} constraint exists
     * for. A naive {@code minLength}-only validation would incorrectly accept this input; this
     * test would fail (get {@code 200} instead of {@code 400}) if that pattern constraint were
     * ever removed or weakened, catching the exact gap the design doc flagged.
     */
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

    /**
     * Posts a message of exactly 200 characters — the contract's {@code maxLength}, so the
     * largest input that must still be accepted.
     *
     * <p>Verifies: the boundary value is accepted ({@code 200}, not incorrectly rejected as
     * "too long"), and the full request/response round-trip through real JSON
     * serialization/deserialization and Spring MVC produces the correct echoed message,
     * length, and shout — this is the happy-path companion to test 5 below, which checks the
     * same boundary from the rejected side.
     */
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

    /**
     * Posts a message of exactly 201 characters — one character past the {@code maxLength}
     * limit.
     *
     * <p>Verifies: the limit is enforced as an exclusive upper bound, not off-by-one in either
     * direction. Paired directly with the 200-character test above: together they pin the
     * accept/reject boundary to exactly between 200 and 201, rather than trusting the schema's
     * {@code maxLength: 200} declaration alone to behave as expected at runtime.
     */
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

    /**
     * Posts a message mixing accented Latin, CJK, and an emoji (a surrogate-pair character),
     * sent as real UTF-8-encoded JSON over an actual HTTP connection (unlike the unit test's
     * in-JVM {@code String}, this one also exercises Jackson's (de)serialization of the
     * request and response bodies).
     *
     * <p>Verifies: the full HTTP round-trip — JSON parsing, Bean Validation against the
     * pattern/length constraints, the service call, and JSON serialization of the response —
     * preserves the message exactly and reports the same UTF-16-code-unit {@code length} that
     * plain Java {@code String#length()} would give, with no encoding corruption introduced by
     * going over the wire.
     */
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

    /**
     * Posts an already-uppercase message over real HTTP.
     *
     * <p>Verifies: the same idempotent-uppercasing behavior checked in the unit test also
     * holds end-to-end through the controller and JSON layers — {@code shout} isn't
     * accidentally altered (e.g. by a double-encoding bug) on its way out over HTTP.
     */
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

    /**
     * Posts {@code "  hi  "} over real HTTP.
     *
     * <p>Verifies: the trim-before-measuring behavior pinned down at the unit level
     * ({@link za.co.tsa.oidcdemo.service.EchoServiceTest#leadingAndTrailingWhitespaceIsTrimmedBeforeLengthAndShoutAreComputed})
     * also holds when the request actually goes through Bean Validation and JSON
     * (de)serialization — i.e. nothing upstream of the service (Jackson, validation) trims or
     * fails to trim the value differently than the service itself does.
     */
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

    /**
     * Posts a valid message with no {@code Authorization} header at all — no bearer token, no
     * session cookie.
     *
     * <p>Verifies: the endpoint is genuinely public per the {@code security: []} declaration
     * in {@code openapi.yaml} and the existing {@code /api/public/**} matcher in
     * {@code SecurityConfig} — the request succeeds ({@code 200}), rather than being blocked
     * by Spring Security as it would be for a {@code /api/user/**} or {@code /api/admin/**}
     * endpoint. This is the security-boundary check for this endpoint, mirroring
     * {@code ControllerSecurityTest#publicEndpointIsOpen()} for {@code /api/public/hello}.
     */
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
