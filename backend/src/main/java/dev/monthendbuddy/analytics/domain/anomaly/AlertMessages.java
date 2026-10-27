package dev.monthendbuddy.analytics.domain.anomaly;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import dev.monthendbuddy.analytics.RuleId;

/** Turns an alert's explanation payload into one plain sentence; it never adds a reason that is not in the payload. */
public final class AlertMessages {

	private static final Locale INDIA = Locale.of("en", "IN");

	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH);

	private AlertMessages() {
	}

	public static String render(RuleId rule, String subjectLabel, LocalDate periodStart, Map<String, Object> e) {
		return switch (rule) {
			case LARGE_VS_CATEGORY -> money(e.get("amount")) + " at " + subjectLabel + " is " + e.get("ratio")
					+ "× your typical " + e.get("category") + " expense of " + money(e.get("median")) + " (median of "
					+ e.get("sampleSize") + " in the last " + e.get("windowDays") + " days).";
			case CATEGORY_SPIKE -> {
				String months = e.get("monthsInBaseline") + " month"
						+ ("1".equals(String.valueOf(e.get("monthsInBaseline"))) ? "" : "s");
				String start = e.get("category") + " spending in " + MONTH.format(periodStart) + " is "
						+ money(e.get("monthTotal"));
				yield e.get("pctOver") == null ? start + ", with none in the previous " + months + "."
						: start + ", " + money(e.get("increase")) + " (+" + e.get("pctOver") + "%) above its usual "
								+ money(e.get("baseline")) + " a month over the previous " + months + ".";
			}
			case ABSOLUTE_THRESHOLD -> money(e.get("amount")) + " at " + subjectLabel
					+ " is above your large-transaction threshold of " + money(e.get("threshold")) + ".";
		};
	}

	private static String money(Object amount) {
		NumberFormat format = NumberFormat.getCurrencyInstance(INDIA);
		format.setMinimumFractionDigits(2);
		format.setMaximumFractionDigits(2);
		return format.format(new BigDecimal(String.valueOf(amount)));
	}

}
