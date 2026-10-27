package dev.monthendbuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Modular monolith (architecture §4). Each direct sub-package is an application module; {@code common} is the
 * shared kernel every module may use. Allowed dependencies are declared in each module's {@code package-info.java}
 * and verified by {@code ModularityTests}.
 */
@SpringBootApplication
@Modulithic(sharedModules = "common")
public class MonthEndBuddyApplication {

	public static void main(String[] args) {
		SpringApplication.run(MonthEndBuddyApplication.class, args);
	}

}
