package dev.monthendbuddy.transactions;

/** Lifecycle of a CSV import (LLD §4.2). */
public enum ImportStatus {

	VALIDATED, COMMITTED, DISCARDED, FAILED

}
