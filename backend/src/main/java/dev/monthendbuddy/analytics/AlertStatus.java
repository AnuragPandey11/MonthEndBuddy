package dev.monthendbuddy.analytics;

/** An alert is open until the user dismisses it or marks it expected; neither re-fires for that subject and month. */
public enum AlertStatus {

	OPEN, DISMISSED, EXPECTED

}
