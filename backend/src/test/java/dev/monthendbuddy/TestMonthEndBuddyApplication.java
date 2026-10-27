package dev.monthendbuddy;

import org.springframework.boot.SpringApplication;

import dev.monthendbuddy.support.TestcontainersConfiguration;

/** Runs the app locally against a throwaway Testcontainers PostgreSQL: {@code ./mvnw spring-boot:test-run}. */
public class TestMonthEndBuddyApplication {

	public static void main(String[] args) {
		SpringApplication.from(MonthEndBuddyApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
