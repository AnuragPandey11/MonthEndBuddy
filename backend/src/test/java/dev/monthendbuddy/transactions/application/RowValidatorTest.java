package dev.monthendbuddy.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.error.FieldViolation;
import dev.monthendbuddy.transactions.DateFormatOption;
import dev.monthendbuddy.transactions.TransactionType;
import dev.monthendbuddy.transactions.application.RowValidator.ParsedRow;
import dev.monthendbuddy.transactions.infrastructure.CsvTransactionParser.RawCsvRow;

class RowValidatorTest {

	private static final UUID DINING = UUID.randomUUID();

	private static final UUID SALARY = UUID.randomUUID();

	private final RowValidator validator = new RowValidator();

	private ParsedRow validate(DateFormatOption format, String... keyValues) {
		Map<String, String> values = new HashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			values.put(keyValues[i], keyValues[i + 1]);
		}
		var context = new RowValidator.Context(format, LocalDate.of(2026, 10, 26), CurrencyCode.INR,
				Map.of("dining", Map.of("EXPENSE", DINING), "salary", Map.of("INCOME", SALARY)));
		return validator.validate(new RawCsvRow(2, values), context);
	}

	private ParsedRow iso(String... keyValues) {
		return validate(DateFormatOption.ISO_YYYY_MM_DD, keyValues);
	}

	private static List<String> errorFields(ParsedRow row) {
		return row.errors().stream().map(FieldViolation::field).toList();
	}

	@Test
	void signDecidesTheTypeWhenThereIsNoTypeColumn() {
		ParsedRow expense = iso("date", "2026-09-03", "description", "Dinner", "amount", "-1250.50");
		assertThat(expense.isValid()).isTrue();
		assertThat(expense.type()).isEqualTo(TransactionType.EXPENSE);
		assertThat(expense.amount()).isEqualByComparingTo("1250.50");
		assertThat(iso("date", "2026-09-01", "description", "Pay", "amount", "95000").type())
			.isEqualTo(TransactionType.INCOME);
		assertThat(errorFields(iso("date", "2026-09-01", "description", "x", "amount", "0"))).containsExactly("amount");
	}

	@Test
	void anExplicitTypeNeedsAPositiveAmountAndAcceptsBankAliases() {
		assertThat(iso("date", "2026-09-03", "description", "x", "amount", "10", "type", "debit").type())
			.isEqualTo(TransactionType.EXPENSE);
		assertThat(errorFields(iso("date", "2026-09-03", "description", "x", "amount", "-10", "type", "EXPENSE")))
			.containsExactly("amount");
		assertThat(errorFields(iso("date", "2026-09-03", "description", "x", "amount", "10", "type", "refund")))
			.containsExactly("type");
	}

	@Test
	void amountsMayUseRupeeSignsAndGroupingButAtMostTwoDecimals() {
		assertThat(iso("date", "2026-09-03", "description", "x", "amount", "-₹1,25,000.50").amount())
			.isEqualByComparingTo("125000.50");
		assertThat(errorFields(iso("date", "2026-09-03", "description", "x", "amount", "-10.005")))
			.containsExactly("amount");
		assertThat(errorFields(iso("date", "2026-09-03", "description", "x", "amount", "1e5"))).containsExactly("amount");
	}

	@Test
	void datesFollowTheChosenFormatStrictlyAndMustNotBeInTheFuture() {
		assertThat(validate(DateFormatOption.DD_MM_YYYY, "date", "30/09/2026", "description", "x", "amount", "-1")
			.date()).isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(errorFields(validate(DateFormatOption.DD_MM_YYYY, "date", "31/02/2026", "description", "x",
				"amount", "-1"))).containsExactly("date");
		assertThat(errorFields(iso("date", "30/09/2026", "description", "x", "amount", "-1"))).containsExactly("date");
		assertThat(iso("date", "2026-10-27", "description", "x", "amount", "-1").errors())
			.containsExactly(new FieldViolation("date", "is in the future"));
	}

	@Test
	void reportsEveryProblemAtOnce() {
		ParsedRow row = iso("date", "nope", "description", " ", "amount", "abc", "currency", "USD");
		assertThat(errorFields(row)).containsExactlyInAnyOrder("date", "description", "amount", "currency");
	}

	@Test
	void unknownOrMismatchedCategoryIsAWarningNotAnError() {
		ParsedRow known = iso("date", "2026-09-03", "description", "x", "amount", "-10", "category", "DINING");
		assertThat(known.categoryId()).isEqualTo(DINING);
		ParsedRow unknown = iso("date", "2026-09-03", "description", "x", "amount", "-10", "category", "Pets");
		assertThat(unknown.isValid()).isTrue();
		assertThat(unknown.categoryId()).isNull();
		assertThat(unknown.warnings()).singleElement().satisfies(w -> assertThat(w.field()).isEqualTo("category"));
		ParsedRow wrongKind = iso("date", "2026-09-03", "description", "x", "amount", "-10", "category", "Salary");
		assertThat(wrongKind.categoryId()).isNull();
		assertThat(wrongKind.warnings()).hasSize(1);
	}

}
