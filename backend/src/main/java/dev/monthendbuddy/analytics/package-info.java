/**
 * Summaries, comparisons, affordability and anomaly rules (architecture §9, LLD §7).
 */
@ApplicationModule(displayName = "Analytics",
		allowedDependencies = { "identity", "transactions", "transactions::events", "budgets", "goals" })
package dev.monthendbuddy.analytics;

import org.springframework.modulith.ApplicationModule;
