package dev.monthendbuddy.goals;

/** Outcome of a savings projection (architecture §6.3). */
public enum ProjectionStatus {

	/** The contribution reaches the target by the target month. */
	ON_TRACK,
	/** The contribution reaches the target, but after the target month. */
	BEHIND,
	ACHIEVED,
	/** The target month is the current month or earlier. */
	DEADLINE_PASSED,
	/** A zero contribution never reaches the target. */
	NEVER

}
