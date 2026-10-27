package dev.monthendbuddy.goals;

import dev.monthendbuddy.common.Money;

/**
 * Money already put toward the user's active goals (architecture §9.4): part of their savings, but set aside, so a
 * purchase simulation does not treat it as free to spend. {@code goals} is how many goals it spans.
 */
public record GoalSavings(Money total, int goals) {
}
