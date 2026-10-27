package dev.monthendbuddy.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.json.JsonMapper;

/**
 * Base class for integration tests (*IT): full application context against a Testcontainers PostgreSQL, with the
 * {@code test} profile and a {@link MutableClock}. Subclasses with the same configuration share one cached context,
 * so the container starts once per run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class })
@ActiveProfiles("test")
public abstract class IntegrationTest {

	@LocalServerPort
	protected int apiPort;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected JsonMapper jsonMapper;

	protected ApiClient api() {
		return new ApiClient(apiPort, jsonMapper);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

}
