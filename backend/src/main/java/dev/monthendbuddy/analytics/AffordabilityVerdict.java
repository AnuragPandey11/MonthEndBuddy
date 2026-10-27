package dev.monthendbuddy.analytics;

/** Outcome of a purchase simulation (architecture §9.4). */
public enum AffordabilityVerdict {

	/** Savings stay above the emergency buffer and the monthly surplus covers every goal. */
	AFFORDABLE,
	/** Savings stay non-negative, but drop below the buffer or goals lose their full contribution. */
	AFFORDABLE_WITH_TRADEOFFS,
	/** Savings would go negative. */
	NOT_AFFORDABLE,
	/** Current savings are unknown, or there is no complete month of data. */
	INSUFFICIENT_DATA

}
