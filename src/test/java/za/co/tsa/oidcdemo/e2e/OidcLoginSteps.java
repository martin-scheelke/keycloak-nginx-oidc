package za.co.tsa.oidcdemo.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Cucumber step definitions for the OIDC login/logout scenarios (see {@code features/*.feature}
 * and {@link CucumberRunnerIT}). Drives a real headless Chrome browser through the actual
 * authorization-code login redirect to Keycloak and back, against a running docker-compose
 * stack — the one layer of the test pyramid that exercises the real NGINX + Keycloak + browser
 * redirect chain end-to-end, which no MockMvc- or Testcontainers-only test can.
 */
public class OidcLoginSteps {

    private final SeleniumWorld world = new SeleniumWorld();
    private final ObjectMapper json = new ObjectMapper();

    private HttpResponse<String> lastApiResponse;

    /** Closes the Chrome session after every scenario so browser state never leaks between
     * scenarios and the process doesn't accumulate orphaned browser instances. */
    @After
    public void tearDown() {
        world.quit();
    }

    // ---- Given ------------------------------------------------------------

    /**
     * Precondition step: hits the public API endpoint directly with a plain JDK
     * {@link HttpClient} (bypassing the browser entirely) before any browser-driven step runs.
     *
     * <p>Verifies: the docker-compose stack is actually up and answering, with a failure
     * message that says exactly that — so if the stack isn't running, this scenario fails
     * immediately with "is `docker compose up` running?" instead of a confusing timeout deep
     * inside a later Selenium step.
     */
    @Given("the demo stack is reachable")
    public void theDemoStackIsReachable() throws Exception {
        HttpResponse<String> res = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(SeleniumWorld.BASE_URL + "/api/public/hello"))
                        .timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode())
                .as("GET /api/public/hello - is `docker compose up` running?")
                .isEqualTo(200);
    }

    /**
     * Navigates the headless browser to the given path on the app (e.g. {@code /secured}) —
     * the starting point for a scenario that expects to be bounced to the Keycloak login page.
     */
    @Given("I open the protected page {string}")
    public void iOpenTheProtectedPage(String path) {
        world.driver().get(SeleniumWorld.BASE_URL + path);
    }

    // ---- When -----------------------------------------------------------

    /**
     * Drives Keycloak's own login form: waits for the username field to render, types the
     * username and password, clicks the login button, then waits for the browser to navigate
     * back to the app's own origin.
     *
     * <p>This simulates a real human completing the OIDC authorization-code flow — it doesn't
     * call any token endpoint directly, it interacts with the actual rendered Keycloak page,
     * so it would catch a broken login form just as much as a broken redirect.
     */
    @When("I sign in as {string} with password {string}")
    public void iSignInAs(String username, String password) {
        WebDriverWait wait = new WebDriverWait(world.driver(), Duration.ofSeconds(15));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")))
                .sendKeys(username);
        world.driver().findElement(By.id("password")).sendKeys(password);
        world.driver().findElement(By.id("kc-login")).click();
        wait.until(ExpectedConditions.urlContains(SeleniumWorld.BASE_URL));
    }

    /**
     * Navigates to the app root and clicks the logout button, then waits for the browser to
     * land back on the root URL — simulates a user-initiated (RP-initiated) logout through the
     * actual UI, not by clearing cookies programmatically.
     */
    @When("I log out")
    public void iLogOut() {
        world.driver().get(SeleniumWorld.BASE_URL + "/");
        new WebDriverWait(world.driver(), Duration.ofSeconds(10))
                .until(ExpectedConditions.elementToBeClickable(By.id("logout-button")))
                .click();
        new WebDriverWait(world.driver(), Duration.ofSeconds(10))
                .until(ExpectedConditions.urlToBe(SeleniumWorld.BASE_URL + "/"));
    }

    // ---- Then -----------------------------------------------------------

    /**
     * Waits for Keycloak's login form to render, then asserts the current browser URL is
     * actually on this realm's Keycloak endpoint.
     *
     * <p>Verifies: the unauthenticated-access redirect (see
     * {@code protectedPageRedirectsToKeycloak} in {@code ControllerSecurityTest} for the
     * mocked/unit-level version of this same guarantee) really lands the browser on Keycloak's
     * login page, not merely on some 3xx response — checked by both the presence of the login
     * form element and the URL shape.
     */
    @Then("my browser is on the Keycloak login page")
    public void myBrowserIsOnTheKeycloakLoginPage() {
        WebDriverWait wait = new WebDriverWait(world.driver(), Duration.ofSeconds(15));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("kc-form-login")));
        assertThat(world.driver().getCurrentUrl())
                .contains("/realms/demo/protocol/openid-connect/");
    }

    /**
     * Waits for the browser to navigate back to the given app path, tolerating a transient
     * query parameter Spring Security may append to the callback URL on the way back from
     * Keycloak.
     *
     * <p>Verifies: after the login (or logout) round trip through Keycloak, the browser
     * actually ends up back on the expected page of the app — confirming the redirect-back
     * half of the OIDC flow completed, not just the redirect-away half checked above.
     */
    @Then("I land back on the application at {string}")
    public void iLandBackOnTheApplicationAt(String path) {
        // Spring Security may append a transient query param (e.g. ?continue) on the way back.
        new WebDriverWait(world.driver(), Duration.ofSeconds(15))
                .until(ExpectedConditions.urlContains(SeleniumWorld.BASE_URL + path));
        assertThat(world.driver().getCurrentUrl())
                .startsWith(SeleniumWorld.BASE_URL + path);
    }

    /**
     * Reads the username text rendered on the secured page (bound to whoever is currently
     * logged in) and compares it to the expected username.
     *
     * <p>Verifies: the page genuinely reflects the identity of the user who just logged in —
     * not merely that *some* page loaded without an error, but that it's personalized
     * correctly for this specific principal.
     */
    @Then("the secured page greets {string}")
    public void theSecuredPageGreets(String username) {
        String shown = world.driver().findElement(By.id("username")).getText();
        assertThat(shown).isEqualTo(username);
    }

    /**
     * Calls an API path using the browser's own session cookies (see {@link #callApi}) and
     * asserts both the HTTP status and that the {@code roles} array in the JSON body matches
     * the given comma-separated list exactly, ignoring order.
     *
     * <p>Verifies: the authenticated browser session — established purely through the UI login
     * flow above, not by minting a token — is honored by the REST API itself (not just by
     * server-rendered pages), and that the role claims baked into that session's token flow
     * through to the API response correctly for this specific logged-in user.
     */
    @And("calling {string} returns HTTP {int} and lists the roles {string}")
    public void callingReturnsHttpAndLists(String path, int status, String csvRoles) throws Exception {
        callApi(path);
        assertThat(lastApiResponse.statusCode()).isEqualTo(status);

        JsonNode body = json.readTree(lastApiResponse.body());
        Set<String> actual = new TreeSet<>();
        body.path("roles").forEach(n -> actual.add(n.asText()));
        Set<String> expected = new TreeSet<>(Arrays.asList(csvRoles.split(",")));
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    /**
     * Calls an API path using the browser's own session cookies and asserts only the HTTP
     * status, with no expectation about the response body.
     *
     * <p>Verifies: cases where only success/failure matters — most notably, confirming a
     * non-admin browser session gets {@code 403} from the admin endpoint, mirroring
     * {@code adminEndpointForbiddenForNonAdminBrowserSession} in {@code ControllerSecurityTest}
     * but through a real login-derived session cookie instead of a mocked principal.
     */
    @And("calling {string} returns HTTP {int}")
    public void callingReturnsHttp(String path, int status) throws Exception {
        callApi(path);
        assertThat(lastApiResponse.statusCode()).isEqualTo(status);
    }

    /**
     * Calls an API endpoint reusing the browser's authenticated session, by copying the
     * Selenium cookies into a plain {@link HttpClient} (which, unlike WebDriver, exposes the
     * real HTTP status code).
     */
    private void callApi(String path) throws Exception {
        CookieManager cookieManager = new CookieManager();
        for (Cookie c : world.driver().manage().getCookies()) {
            HttpCookie httpCookie = new HttpCookie(c.getName(), c.getValue());
            httpCookie.setPath(c.getPath() == null ? "/" : c.getPath());
            httpCookie.setVersion(0);
            cookieManager.getCookieStore().add(URI.create(SeleniumWorld.BASE_URL), httpCookie);
        }
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        lastApiResponse = client.send(
                HttpRequest.newBuilder(URI.create(SeleniumWorld.BASE_URL + path))
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (lastApiResponse.statusCode() == 302) {
            fail("Expected an API response but got a redirect to "
                    + lastApiResponse.headers().firstValue("location").orElse("?")
                    + " - the browser session cookie was not accepted");
        }
    }
}
