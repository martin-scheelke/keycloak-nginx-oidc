package za.co.tsa.oidcdemo.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import org.junit.jupiter.api.Test;

/**
 * Guards the hand-authored contract. The Spring {@code *Api} interfaces and models are
 * generated from this file at build time, so it must always parse cleanly and keep the
 * operations the controllers implement.
 */
class OpenApiSpecValidationTest {

    private static final Path SPEC = Path.of("openapi", "openapi.yaml");

    /**
     * Checks that {@code openapi/openapi.yaml} exists on disk at the path every other tool in
     * this project (the generator, this test class, {@link OpenApiDocsTest}) assumes it lives
     * at.
     *
     * <p>Verifies: the simplest possible thing that could go wrong — the file being renamed,
     * moved, or deleted — fails here with a clear message, instead of surfacing as a confusing
     * generator or build failure elsewhere.
     */
    @Test
    void specFileExists() {
        assertThat(Files.isRegularFile(SPEC))
                .as("openapi/openapi.yaml must exist - it is the source of truth")
                .isTrue();
    }

    /**
     * Parses the spec with {@code $ref} resolution and external-reference validation both
     * turned on.
     *
     * <p>Verifies: zero parser messages (errors or warnings) and a non-null parsed model —
     * catching YAML syntax mistakes, broken/unresolved {@code $ref}s, or invalid schema
     * constructs (e.g. a malformed {@code pattern}) as an immediate, clearly-attributed test
     * failure, rather than a much less obvious failure later when
     * {@code openapi-generator-maven-plugin} tries to generate code from the same file.
     */
    @Test
    void specIsValidOpenApiWithNoParsingErrors() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setValidateExternalRefs(true);

        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(SPEC.toUri().toString(), null, options);

        assertThat(result.getMessages()).as("parser errors/warnings").isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
    }

    /**
     * Parses the spec and inspects its structure directly (not via HTTP, not via the generated
     * code) for the three endpoints known to be implemented and both security schemes the app
     * relies on.
     *
     * <p>Verifies: the contract still declares exactly the {@code operationId}s the controllers
     * actually implement ({@code getPublicHello}, {@code getCurrentUser}, {@code
     * getAdminStats}) and both {@code bearer-jwt}/{@code keycloak-oidc} security schemes — a
     * guard against the spec and the Java controllers silently drifting apart, e.g. someone
     * renaming an {@code operationId} (which would break the generated interface's method
     * name) or removing a security scheme the app still references in {@code SecurityConfig}.
     */
    @Test
    void specDeclaresEveryImplementedOperationAndSecurityScheme() {
        OpenAPI api = new OpenAPIV3Parser().readLocation(SPEC.toUri().toString(), null, new ParseOptions()).getOpenAPI();

        assertThat(api.getPaths()).containsKeys("/api/public/hello", "/api/user/me", "/api/admin/stats");
        assertThat(api.getPaths().get("/api/public/hello").getGet().getOperationId()).isEqualTo("getPublicHello");
        assertThat(api.getPaths().get("/api/user/me").getGet().getOperationId()).isEqualTo("getCurrentUser");
        assertThat(api.getPaths().get("/api/admin/stats").getGet().getOperationId()).isEqualTo("getAdminStats");
        assertThat(api.getComponents().getSecuritySchemes()).containsKeys("bearer-jwt", "keycloak-oidc");
    }
}
