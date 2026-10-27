/**
 * Synthetic demo data: the deterministic generator, the demo account's seeding and nightly reset, and the sample CSV
 * export (implementation plan P1.5). Nothing depends on this module.
 */
@ApplicationModule(displayName = "Demo", allowedDependencies = { "identity", "transactions", "budgets", "goals" })
package dev.monthendbuddy.demo;

import org.springframework.modulith.ApplicationModule;
