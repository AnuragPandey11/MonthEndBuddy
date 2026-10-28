package dev.monthendbuddy.analytics.events;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.monthendbuddy.common.events.EventType;

/** The analytics module's events on Kafka (architecture §12.3). */
@Configuration(proxyBeanMethods = false)
class AnalyticsEventTypes {

	@Bean
	EventType anomalyDetectedType() {
		return new EventType("AnomalyDetected", AnomalyDetected.class, "meb.alerts.v1", 1);
	}

}
