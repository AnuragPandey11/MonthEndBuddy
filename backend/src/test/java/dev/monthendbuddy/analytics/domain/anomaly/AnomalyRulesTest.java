package dev.monthendbuddy.analytics.domain.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.analytics.SubjectType;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;

/** The three rules at their boundaries (architecture §9.5, plan P2.8). */
class AnomalyRulesTest {

	private static final AnomalyContext.AnomalySettings DEFAULTS = new AnomalyContext.AnomalySettings(
			new BigDecimal("3.0"), 40, Money.of("25000", CurrencyCode.INR), Money.of("2000", CurrencyCode.INR));

	private static Money inr(String amount) {
		return Money.of(amount, CurrencyCode.INR);
	}

	private static List<AnomalyContext.WindowExpense> samples(int n, String amount) {
		return Collections.nCopies(n, new AnomalyContext.WindowExpense(new BigDecimal(amount), "Cafe"));
	}

	private static AnomalyContext expense(String amount, List<AnomalyContext.WindowExpense> window, String monthTotal,
			String baseline,
			int baselineMonths) {
		return new AnomalyContext(UUID.randomUUID(), LocalDate.of(2026, 9, 14), inr(amount), UUID.randomUUID(), "Dining",
				"The Leela Palace", window, inr(monthTotal), baseline == null ? null : inr(baseline), baselineMonths,
				DEFAULTS);
	}

	@Test
	void largeVsCategoryFiresAboveKTimesTheMedianWithFiveSamples() {
		LargeVsCategoryRule rule = new LargeVsCategoryRule();
		assertThat(rule.evaluate(expense("800", samples(5, "200"), "0", null, 0))).hasValueSatisfying(f -> {
			assertThat(f.subjectType()).isEqualTo(SubjectType.TRANSACTION);
			assertThat(f.periodStart()).isEqualTo(LocalDate.of(2026, 9, 1));
			assertThat(f.explanation()).containsEntry("median", "200.00").containsEntry("ratio", "4.0")
				.containsEntry("sampleSize", 5);
		});
		assertThat(rule.evaluate(expense("600", samples(5, "200"), "0", null, 0))).as("exactly k× does not fire")
			.isEmpty();
		assertThat(rule.evaluate(expense("800", samples(4, "200"), "0", null, 0))).as("four samples are too few")
			.isEmpty();
	}

	@Test
	void medianOfAnEvenNumberOfSamplesIsTheMeanOfTheMiddleTwo() {
		assertThat(LargeVsCategoryRule.median(List.of(new BigDecimal("100"), new BigDecimal("400"),
				new BigDecimal("200"), new BigDecimal("300")))).isEqualByComparingTo("250");
	}

	@Test
	void categorySpikeNeedsFortyPercentAndTwoThousandRupees() {
		CategorySpikeRule rule = new CategorySpikeRule();
		assertThat(rule.evaluate(expense("1", List.of(), "10000", "5000", 3))).hasValueSatisfying(f -> {
			assertThat(f.subjectType()).isEqualTo(SubjectType.CATEGORY);
			assertThat(f.explanation()).containsEntry("increase", "5000.00").containsEntry("pctOver", new BigDecimal("100"));
		});
		assertThat(rule.evaluate(expense("1", List.of(), "7000", "5000", 3))).as("exactly 40% over").isEmpty();
		assertThat(rule.evaluate(expense("1", List.of(), "4999", "3000", 3))).as("₹1,999 increase").isEmpty();
		assertThat(rule.evaluate(expense("1", List.of(), "5000", "3000", 3))).as("₹2,000 increase").isPresent();
	}

	@Test
	void categorySpikeFromNothingOnceTheIncreaseIsLargeEnoughButNeverWithoutABaseline() {
		CategorySpikeRule rule = new CategorySpikeRule();
		assertThat(rule.evaluate(expense("18000", List.of(), "18000", "0", 3)))
			.hasValueSatisfying(f -> assertThat(f.explanation().get("pctOver")).isNull());
		assertThat(rule.evaluate(expense("1500", List.of(), "1500", "0", 3))).isEmpty();
		assertThat(rule.evaluate(expense("18000", List.of(), "18000", null, 0))).as("no months of data before")
			.isEmpty();
	}

	@Test
	void routinePaymentsToTheSameMerchantDoNotAlert() {
		List<AnomalyContext.WindowExpense> rides = new java.util.ArrayList<>(samples(6, "300"));
		AnomalyContext fuel = expense("2800", rides, "0", null, 0);
		assertThat(new LargeVsCategoryRule().evaluate(fuel)).isPresent();
		rides.add(new AnomalyContext.WindowExpense(new BigDecimal("1500"), "the leela palace"));
		assertThat(new LargeVsCategoryRule().evaluate(expense("2800", rides, "0", null, 0)))
			.as("a similar earlier payment to the same merchant makes it routine")
			.isEmpty();
		AnomalyContext rent = expense("25000", List.of(new AnomalyContext.WindowExpense(new BigDecimal("25000"),
				"The Leela Palace")), "0", null, 0);
		assertThat(new AbsoluteThresholdRule().evaluate(rent)).isEmpty();
	}

	@Test
	void absoluteThresholdFiresAtTheThreshold() {
		AbsoluteThresholdRule rule = new AbsoluteThresholdRule();
		assertThat(rule.evaluate(expense("25000", List.of(), "0", null, 0))).isPresent();
		assertThat(rule.evaluate(expense("24999.99", List.of(), "0", null, 0))).isEmpty();
	}

	@Test
	void messagesAreRenderedOnlyFromThePayload() {
		var finding = new LargeVsCategoryRule().evaluate(expense("8400", samples(14, "800"), "0", null, 0)).orElseThrow();
		assertThat(AlertMessages.render(finding.ruleId(), "The Leela Palace", finding.periodStart(), finding.explanation()))
			.isEqualTo("₹8,400.00 at The Leela Palace is 10.5× your typical Dining expense of ₹800.00 "
					+ "(median of 14 in the last 90 days).");
		var absolute = new AbsoluteThresholdRule().evaluate(expense("62000", List.of(), "0", null, 0)).orElseThrow();
		assertThat(AlertMessages.render(absolute.ruleId(), "Croma", absolute.periodStart(), absolute.explanation()))
			.isEqualTo("₹62,000.00 at Croma is above your large-transaction threshold of ₹25,000.00.");
	}

}
