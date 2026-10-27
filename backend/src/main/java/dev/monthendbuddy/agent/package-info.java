/**
 * AI copilot: orchestrator, tool registry, LLM gateway, conversations (architecture §10, LLD §8). Nothing may
 * depend on this module, so the application works with the AI removed.
 */
@ApplicationModule(displayName = "Agent",
		allowedDependencies = { "identity", "transactions", "budgets", "goals", "analytics" })
package dev.monthendbuddy.agent;

import org.springframework.modulith.ApplicationModule;
