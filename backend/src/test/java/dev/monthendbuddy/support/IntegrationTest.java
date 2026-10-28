package dev.monthendbuddy.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.json.JsonMapper;

/**
 * Base class for integration tests (*IT): full application context against a Testcontainers PostgreSQL, with the
 * {@code test} profile and a {@link MutableClock}. Subclasses with the same configuration share one cached context,
 * so the container starts once per run.
 * <p>
 * Tests that read the effects of event handlers call {@link #awaitEventsHandled()} after a write. In-process events
 * are handled before the write returns, so it does nothing; with {@code meb.events.mode=kafka}
 * ({@link KafkaIntegrationTest}) it waits for the outbox relay and the consumers.
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

	@Autowired
	private ApplicationContext context;

	protected ApiClient api() {
		return new ApiClient(apiPort, jsonMapper);
	}

	protected void awaitEventsHandled() {
		context.getBeanProvider(KafkaTestEvents.class).ifAvailable(KafkaTestEvents::awaitHandled);
	}

	/** Lets a Kafka consumer finish the last test's events before the next test moves the clock. */
	@AfterEach
	void resetClock() {
		awaitEventsHandled();
		clock.reset();
	}

}
