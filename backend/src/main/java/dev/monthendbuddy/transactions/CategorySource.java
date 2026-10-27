package dev.monthendbuddy.transactions;

/** Who chose the category. A {@code USER} choice is never overwritten by rules (architecture §8.1). */
public enum CategorySource {

	USER, RULE, AI, IMPORT, NONE

}
