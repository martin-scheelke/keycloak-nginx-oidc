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

public class OidcLoginSteps {

    private final SeleniumWorld world = new SeleniumWorld();
    private final ObjectMapper json = new ObjectMapper();

    private HttpResponse<String> lastApiResponse;

    @After
    public void tearDown() {
        world.quit();
    }

    // ---- Given ------------------------------------------------------------

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

    @Given("I open the protected page {string}")
    public void iOpenTheProtectedPage(String path) {
        world.driver().get(SeleniumWorld.BASE_URL + path);
    }

    // ---- When -----------------------------------------------------------

    @When("I sign in as {string} with password {string}")
    public void iSignInAs(String username, String password) {
        WebDriverWait wait = new WebDriverWait(world.driver(), Duration.ofSeconds(15));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")))
                .sendKeys(username);
        world.driver().findElement(By.id("password")).sendKeys(password);
        world.driver().findElement(By.id("kc-login")).click();
        wait.until(ExpectedConditions.urlContains(SeleniumWorld.BASE_URL));
    }

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

    @Then("my browser is on the Keycloak login page")
    public void myBrowserIsOnTheKeycloakLoginPage() {
        WebDriverWait wait = new WebDriverWait(world.driver(), Duration.ofSeconds(15));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("kc-form-login")));
        assertThat(world.driver().getCurrentUrl())
                .contains("/realms/demo/protocol/openid-connect/");
    }

    @Then("I land back on the application at {string}")
    public void iLandBackOnTheApplicationAt(String path) {
        // Spring Security may append a transient query param (e.g. ?continue) on the way back.
        new WebDriverWait(world.driver(), Duration.ofSeconds(15))
                .until(ExpectedConditions.urlContains(SeleniumWorld.BASE_URL + path));
        assertThat(world.driver().getCurrentUrl())
                .startsWith(SeleniumWorld.BASE_URL + path);
    }

    @Then("the secured page greets {string}")
    public void theSecuredPageGreets(String username) {
        String shown = world.driver().findElement(By.id("username")).getText();
        assertThat(shown).isEqualTo(username);
    }

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
