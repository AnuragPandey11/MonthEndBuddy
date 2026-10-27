package dev.monthendbuddy.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.demo.SyntheticDataGenerator.GeneratedTransaction;
import dev.monthendbuddy.demo.SyntheticDataGenerator.Spec;
import dev.monthendbuddy.transactions.TransactionType;

class SyntheticDataGeneratorTest {

	private static final LocalDate ANCHOR = LocalDate.of(2026, 10, 15);

	/** Names of the seeded system categories (V2__transactions.sql). */
	private static final Set<String> SYSTEM_CATEGORIES = Set.of("Rent", "Groceries", "Dining", "Transport",
			"Utilities", "Shopping", "Entertainment", "Health", "Education", "Travel", "Subscriptions", "Personal Care",
			"Insurance", "EMI & Loans", "Gifts & Donations", "Other Expense", "Salary", "Freelance", "Interest",
			"Refund", "Other Income");

	private final SyntheticDataGenerator generator = new SyntheticDataGenerator();

	private List<GeneratedTransaction> generate(long seed, LocalDate anchor, int profile) {
		return generator.generate(new Spec(seed, anchor, 6, profile));
	}

	@Test
	void sameSpecGivesIdenticalOutputAndDifferentSeedsDiffer() {
		assertThat(SampleCsv.write(generate(7, ANCHOR, 0))).isEqualTo(SampleCsv.write(generate(7, ANCHOR, 0)));
		assertThat(generate(7, ANCHOR, 0)).isNotEqualTo(generate(8, ANCHOR, 0));
		assertThat(generate(7, ANCHOR, 0)).isNotEqualTo(generate(7, ANCHOR, 1));
	}

	@Test
	void coversSixCalendarMonthsAndNeverGoesPastTheAnchor() {
		List<GeneratedTransaction> rows = generate(7, ANCHOR, 0);
		assertThat(rows).allSatisfy(r -> assertThat(r.date()).isBetween(LocalDate.of(2026, 5, 1), ANCHOR));
		assertThat(rows.stream().map(r -> YearMonth.from(r.date())).distinct()).hasSize(6);
		assertThat(rows).isSortedAccordingTo(java.util.Comparator.comparing(GeneratedTransaction::date));
		assertThat(rows.size()).isBetween(150, 300);
	}

	@Test
	void everyMonthHasOneSalaryAndRentAndAmountsAreValid() {
		Map<YearMonth, Long> salaries = generate(7, ANCHOR, 0).stream()
			.filter(r -> r.category().equals("Salary"))
			.collect(Collectors.groupingBy(r -> YearMonth.from(r.date()), Collectors.counting()));
		assertThat(salaries).hasSize(6).allSatisfy((month, count) -> assertThat(count).isEqualTo(1));
		assertThat(generate(7, ANCHOR, 0)).allSatisfy(r -> {
			assertThat(r.amount()).isPositive();
			assertThat(r.amount().scale()).isEqualTo(2);
			assertThat(SYSTEM_CATEGORIES).contains(r.category());
			boolean incomeCategory = Set.of("Salary", "Freelance", "Interest").contains(r.category());
			assertThat(r.type()).isEqualTo(incomeCategory ? TransactionType.INCOME : TransactionType.EXPENSE);
		});
	}

	@Test
	void plantedAnomaliesArePresentForTheAnomalyRules() {
		List<GeneratedTransaction> rows = generate(7, ANCHOR, 0);
		assertThat(rows).anySatisfy(r -> {
			assertThat(r.category()).isEqualTo("Dining");
			assertThat(r.amount()).isEqualByComparingTo(new BigDecimal("8400"));
			assertThat(YearMonth.from(r.date())).isEqualTo(YearMonth.of(2026, 9));
		});
		assertThat(rows).anySatisfy(r -> assertThat(r.amount()).isEqualByComparingTo(new BigDecimal("62000")));
		assertThat(rows).anySatisfy(r -> {
			assertThat(r.category()).isEqualTo("Travel");
			assertThat(YearMonth.from(r.date())).isEqualTo(YearMonth.of(2026, 10));
		});
	}

	@Test
	void anchorOnTheFirstOfTheMonthStillPlacesTheTripOnOrBeforeIt() {
		LocalDate firstOfMonth = LocalDate.of(2026, 11, 1);
		assertThat(generate(7, firstOfMonth, 0)).allSatisfy(r -> assertThat(r.date()).isBeforeOrEqualTo(firstOfMonth))
			.anySatisfy(r -> assertThat(r.category()).isEqualTo("Travel"));
	}

	@Test
	void rejectsUnsupportedSpecs() {
		assertThatThrownBy(() -> new Spec(1, ANCHOR, 3, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Spec(1, ANCHOR, 6, 2)).isInstanceOf(IllegalArgumentException.class);
	}

	/** Regenerate with {@code ./mvnw -Psample-data process-classes} after changing the generator. */
	@Test
	void committedSampleCsvMatchesTheGenerator() throws Exception {
		Path committed = Path.of("..", "sample-data", "synthetic-transactions.csv");
		String expected = SampleCsv.write(generator.generate(
				new Spec(SampleDataExporter.SEED, SampleDataExporter.ANCHOR, 6, 0)));
		assertThat(Files.readString(committed, StandardCharsets.UTF_8)).isEqualTo(expected);
	}

}
