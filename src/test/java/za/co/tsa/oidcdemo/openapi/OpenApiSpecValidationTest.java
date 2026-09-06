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

    @Test
    void specFileExists() {
        assertThat(Files.isRegularFile(SPEC))
                .as("openapi/openapi.yaml must exist - it is the source of truth")
                .isTrue();
    }

    @Test
    void specIsValidOpenApiWithNoParsingErrors() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setValidateExternalRefs(true);

        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(SPEC.toUri().toString(), null, options);

        assertThat(result.getMessages()).as("parser errors/warnings").isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
    }

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
