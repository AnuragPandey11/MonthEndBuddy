package dev.monthendbuddy;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Enforces the module dependency rules declared in each module's package-info (architecture §4.1). */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(MonthEndBuddyApplication.class);

	@Test
	void moduleBoundariesAreRespected() {
		modules.verify();
	}

}
