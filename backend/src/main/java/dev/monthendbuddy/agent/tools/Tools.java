package dev.monthendbuddy.agent.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.monthendbuddy.common.Assumption;
import dev.monthendbuddy.common.AssumptionLabel;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.TextSanitizer;
import dev.monthendbuddy.transactions.TransactionAggregations.DataCoverage;

/** Small helpers shared by the tool adapters. */
final class Tools {

	static final String TRANSACTIONS = "transactions";

	private Tools() {
	}

	static Provenance fromTransactions(String period, DataCoverage coverage, ToolContext context, Clock clock) {
		return new Provenance(TRANSACTIONS, period, context.currency(), clock.instant(),
				coverage == null ? null : coverage.transactionCount());
	}

	static List<Assumption> defaultedMonth(MonthInput input, YearMonth month) {
		List<Assumption> assumptions = new ArrayList<>();
		if (input.month() == null) {
			assumptions.add(new Assumption("month", month + " (current month)", AssumptionLabel.DEFAULT_ASSUMPTION));
		}
		return assumptions;
	}

	/** Free text from transactions is untrusted: cleaned of control characters and cut to 120 characters. */
	static final int MAX_TEXT = 120;

	static String text(String value) {
		String cleaned = TextSanitizer.singleLine(value);
		return cleaned == null || cleaned.length() <= MAX_TEXT ? cleaned : cleaned.substring(0, MAX_TEXT - 1) + "…";
	}

	/** A suggested limit for a proposed action: the amount rounded up to the next ₹500. */
	static Money roundUpTo500(Money amount) {
		BigDecimal step = BigDecimal.valueOf(500);
		BigDecimal rounded = amount.amount().divide(step, 0, RoundingMode.CEILING).multiply(step);
		return new Money(rounded.max(step), amount.currency());
	}

	/** Money in the REST request shape, so the UI can pre-fill a form from a proposed action. */
	static Map<String, Object> wire(Money money) {
		return Map.of("amount", money.toApiString(), "currency", money.currency().name());
	}

	/** A model-supplied amount in the user's currency; null stays null. */
	static Money money(BigDecimal amount, ToolContext context) {
		return amount == null ? null : new Money(amount, context.currency());
	}

}
