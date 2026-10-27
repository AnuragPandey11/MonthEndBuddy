/**
 * Budget definitions, budget status and threshold events (architecture §9.2, LLD §5).
 */
@ApplicationModule(displayName = "Budgets", allowedDependencies = { "identity", "transactions", "transactions::events" })
package dev.monthendbuddy.budgets;

import org.springframework.modulith.ApplicationModule;
