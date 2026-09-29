package za.co.tsa.oidcdemo.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The service is contract-first: it serves the hand-authored {@code openapi/openapi.yaml}
 * verbatim as a static resource, and springdoc's runtime doc generation is disabled.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {

    @Autowired
    MockMvc mvc;

    /**
     * Requests {@code /openapi.yaml} with no credentials.
     *
     * <p>Verifies two things at once: the file served at that URL really is the hand-authored
     * contract (checked by looking for its actual title and a known path in the body, rather
     * than a springdoc-generated equivalent that happened to look similar), and it's reachable
     * without authentication — required for API clients and tooling (e.g. codegen, Postman) to
     * fetch the contract without first needing credentials.
     */
    @Test
    void servesTheHandAuthoredContractAnonymously() throws Exception {
        mvc.perform(get("/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("title: Keycloak/NGINX OIDC demo API")))
                .andExpect(content().string(containsString("/api/user/me")));
    }

    /**
     * Requests the Swagger UI HTML page and one of its bundled JavaScript assets, both with no
     * credentials.
     *
     * <p>Verifies: the interactive docs page itself loads, that its HTML actually points at
     * {@code /openapi.yaml} (i.e. it's wired to render *this* project's contract, not a
     * default/blank one), and that the webjar-served JS it depends on is also reachable — a
     * page that loads but whose assets 404 would appear broken to anyone opening it in a
     * browser, which this test would catch.
     */
    @Test
    void swaggerUiPageAndAssetsAreAccessibleAnonymously() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/openapi.yaml")));
        mvc.perform(get("/webjars/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
    }
}
