package dev.monthendbuddy.analytics.domain.anomaly;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import dev.monthendbuddy.common.Money;

/**
 * Everything a rule needs about one expense (LLD §7.2). The category figures are {@code null}/empty for an
 * uncategorised expense.
 *
 * @param categoryWindow the same category's other expenses in the 90 days up to this one
 * @param categoryMonthTotal the category's spending in this expense's month
 * @param categoryBaseline mean monthly category spending over the previous 3 months that have any data, or null
 * @param baselineMonths how many months the baseline averages over
 */
public record AnomalyContext(UUID transactionId, LocalDate date, Money amount, UUID categoryId, String categoryName,
		String merchantOrDescription, List<WindowExpense> categoryWindow, Money categoryMonthTotal,
		Money categoryBaseline, int baselineMonths, AnomalySettings settings) {

	/** Share of this amount a payment to the same merchant must reach for this one to count as routine. */
	public static final BigDecimal ROUTINE_SHARE = new BigDecimal("0.5");

	public List<BigDecimal> categoryWindowAmounts() {
		return categoryWindow.stream().map(WindowExpense::amount).toList();
	}

	/**
	 * A payment the user makes regularly (rent, EMI, subscription, fuel): the same merchant was paid at least half
	 * this amount within the window. Routine payments never raise transaction alerts.
	 */
	public boolean isRoutine() {
		if (merchantOrDescription == null) {
			return false;
		}
		String label = merchantOrDescription.toLowerCase(Locale.ROOT);
		BigDecimal floor = amount.amount().multiply(ROUTINE_SHARE);
		return categoryWindow.stream()
			.anyMatch(w -> w.label() != null && w.label().toLowerCase(Locale.ROOT).equals(label)
					&& w.amount().compareTo(floor) >= 0);
	}

	public record WindowExpense(BigDecimal amount, String label) {
	}

	/** Per-user rule settings (architecture §7.1), a read-only view so rules never see identity's entities. */
	public record AnomalySettings(BigDecimal multiplier, int categorySpikePct, Money largeTxnThreshold,
			Money minSpikeIncrease) {
	}

}
