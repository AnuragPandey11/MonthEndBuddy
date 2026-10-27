package dev.monthendbuddy.common.time;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} jobs: the nightly demo reset now; import-row and outbox purges later. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration {

}
