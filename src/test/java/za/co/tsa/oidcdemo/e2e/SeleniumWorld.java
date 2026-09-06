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

    void quit() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}
