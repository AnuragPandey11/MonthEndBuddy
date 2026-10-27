package dev.monthendbuddy.common.json;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.JacksonModule;

/** Spring Boot registers every {@link JacksonModule} bean with the application's {@code JsonMapper}. */
@Configuration(proxyBeanMethods = false)
class JacksonConfiguration {

	@Bean
	JacksonModule moneyJacksonModule() {
		return new MoneyJacksonModule();
	}

}
