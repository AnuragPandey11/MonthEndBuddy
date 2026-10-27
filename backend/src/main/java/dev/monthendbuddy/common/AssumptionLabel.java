package dev.monthendbuddy.common;

/** Where a calculation input came from. */
public enum AssumptionLabel {

	/** Computed by the backend from the user's transactions. */
	DERIVED,
	/** Stated by the user for this one calculation. */
	USER_STATED,
	/** Stored by the user in their settings or goals; may be stale. */
	USER_PROFILE,
	/** A default the user did not choose, e.g. "0% return". */
	DEFAULT_ASSUMPTION

}
