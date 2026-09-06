package za.co.tsa.oidcdemo.e2e;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Runs the Selenium/Cucumber end-to-end suite against a running docker-compose stack.
 *
 * <p>Excluded from the default build; enable with {@code ./mvnw verify -Pe2e} after
 * {@code docker compose up -d --build} and with Google Chrome installed.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "za.co.tsa.oidcdemo.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, summary")
class CucumberRunnerIT {
}
