package dev.monthendbuddy.common.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application clock, in UTC. Inject {@link Clock} instead of calling {@code now()} so tests can fix time;
 * "today" for a user is derived from it in the user's timezone (architecture §6.2).
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
