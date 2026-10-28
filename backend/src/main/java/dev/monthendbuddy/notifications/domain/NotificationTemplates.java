package dev.monthendbuddy.notifications.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import dev.monthendbuddy.analytics.events.AnomalyDetected;
import dev.monthendbuddy.budgets.events.BudgetThresholdReached;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.events.DomainEvent;
import dev.monthendbuddy.notifications.NotificationType;
import dev.monthendbuddy.transactions.events.TransactionsImported;

/**
 * The text of each notification, built from the event alone (this module sees only the {@code events} packages, so it
 * names no categories or merchants). Links are app paths the SPA routes to.
 */
public final class NotificationTemplates {

	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH);

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

	private NotificationTemplates() {
	}

	public record Content(NotificationType type, String title, String body, String link) {
	}

	/**
	 * Empty for an event that warrants no notification: a threshold crossed in a month before {@code today}'s (an
	 * imported or reseeded history fires one per past month, which is news to no one), or a demo reseed, which is not
	 * the user's import.
	 */
	public static Optional<Content> render(DomainEvent event, LocalDate today) {
		return switch (event) {
			case BudgetThresholdReached e when YearMonth.from(e.periodStart()).isBefore(YearMonth.from(today)) ->
				Optional.empty();
			case BudgetThresholdReached e -> Optional.of(new Content(NotificationType.BUDGET_THRESHOLD,
					e.threshold() >= 100 ? "Budget used up" : "Budget " + e.threshold() + "% used",
					"You've spent " + money(e.spent()) + " of a " + money(e.limit()) + " budget in "
							+ MONTH.format(e.periodStart()) + " (" + percent(e.pctUsed()) + ").",
					"/budgets"));
			case AnomalyDetected e -> Optional.of(new Content(NotificationType.ANOMALY, "Unusual spending",
					anomalyBody(e.ruleId()) + " See the alert on your dashboard for the numbers.", "/"));
			case TransactionsImported e when e.aggregateId().equals(e.userId().value()) -> Optional.empty();
			case TransactionsImported e -> Optional.of(new Content(NotificationType.IMPORT_COMPLETED, "Import complete",
					e.count() + (e.count() == 1 ? " transaction" : " transactions") + " imported, dated "
							+ range(e.firstDate(), e.lastDate()) + ".",
					"/transactions"));
			default -> Optional.empty();
		};
	}

	private static String anomalyBody(String ruleId) {
		return switch (ruleId) {
			case "LARGE_VS_CATEGORY" -> "An expense is far larger than you usually spend in its category.";
			case "CATEGORY_SPIKE" -> "A category's spending this month is well above its usual level.";
			case "ABSOLUTE_THRESHOLD" -> "An expense is above your large-transaction threshold.";
			default -> "Something in your spending looks unusual.";
		};
	}

	private static String range(LocalDate first, LocalDate last) {
		return first.equals(last) ? DAY.format(first) : DAY.format(first) + " to " + DAY.format(last);
	}

	/**
	 * Rupees with Indian grouping, as the app shows them: {@code ₹1,26,750.00}. The JDK's {@code en-IN} format groups
	 * in threes ({@code ₹126,750.00}), so the lakh and crore groups are placed here.
	 */
	static String money(Money amount) {
		String plain = amount.amount().abs().setScale(Money.API_SCALE, Money.ROUNDING).toPlainString();
		String rupees = plain.substring(0, plain.length() - 3);
		StringBuilder grouped = new StringBuilder(rupees.substring(Math.max(0, rupees.length() - 3)));
		for (int end = rupees.length() - 3; end > 0; end -= 2) {
			grouped.insert(0, rupees.substring(Math.max(0, end - 2), end) + ",");
		}
		return (amount.isNegative() ? "-₹" : "₹") + grouped + plain.substring(plain.length() - 3);
	}

	private static String percent(BigDecimal pct) {
		return pct.setScale(1, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString() + "%";
	}

}
