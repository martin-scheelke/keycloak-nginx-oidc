package za.co.tsa.oidcdemo.e2e;

import java.time.Duration;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Holds the browser and the base URL for a single scenario. The base URL defaults to the
 * NGINX entry point of the local docker-compose stack and can be overridden with the
 * {@code APP_BASE_URL} environment variable (e.g. in CI).
 */
final class SeleniumWorld {

    static final String BASE_URL =
            System.getenv().getOrDefault("APP_BASE_URL", "http://localhost:8080");

    private ChromeDriver driver;

    /**
     * Lazily creates (or returns the already-created) headless Chrome session for the current
     * scenario. Headless and sandbox-disabled so this also runs inside a container/CI runner
     * with no display and typically no elevated privileges; {@code CHROME_BIN} lets CI point at
     * a specific installed Chrome binary instead of relying on Selenium Manager to find one.
     */
    ChromeDriver driver() {
        if (driver == null) {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                    "--disable-gpu", "--window-size=1280,1024");
            if (System.getenv("CHROME_BIN") != null) {
                options.setBinary(System.getenv("CHROME_BIN"));
            }
            driver = new ChromeDriver(options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        }
        return driver;
    }

    /** Shuts down the Chrome session if one was ever started, and clears the reference so a
     * later {@link #driver()} call on a reused instance would start a fresh session rather than
     * reuse a dead one. Called from {@link OidcLoginSteps#tearDown()} after every scenario. */
    void quit() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}
