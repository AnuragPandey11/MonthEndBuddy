package dev.monthendbuddy.transactions.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.TextSanitizer;
import dev.monthendbuddy.common.error.FieldViolation;
import dev.monthendbuddy.transactions.DateFormatOption;
import dev.monthendbuddy.transactions.TransactionType;
import dev.monthendbuddy.transactions.infrastructure.CsvTransactionParser.RawCsvRow;

/**
 * Validates one CSV row (architecture §8.2) and reports every problem at once, by field, so the preview can show
 * them all. If there is no {@code type} column, the amount's sign decides: negative is an expense, positive income.
 * An unknown category is a warning, not an error: the row is imported uncategorised (or by the user's rules).
 */
@Component
public class RowValidator {

	private static final Pattern AMOUNT = Pattern.compile("[+-]?\\d{1,15}(\\.\\d{1,2})?");

	private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d/M/uuuu")
		.withResolverStyle(ResolverStyle.STRICT);

	/**
	 * What the validator needs to know about the user.
	 * @param categories the user's usable categories by lower-case name, then by kind ({@code INCOME}/{@code EXPENSE})
	 */
	public record Context(DateFormatOption dateFormat, LocalDate today, CurrencyCode baseCurrency,
			Map<String, Map<String, UUID>> categories) {
	}

	public record ParsedRow(int rowNumber, LocalDate date, TransactionType type, BigDecimal amount,
			CurrencyCode currency, String description, String merchant, String categoryName, UUID categoryId,
			String notes, List<FieldViolation> errors, List<FieldViolation> warnings) {

		public boolean isValid() {
			return errors.isEmpty();
		}

	}

	public ParsedRow validate(RawCsvRow raw, Context context) {
		List<FieldViolation> errors = new ArrayList<>();
		List<FieldViolation> warnings = new ArrayList<>();

		LocalDate date = parseDate(raw.get("date"), context.dateFormat(), errors);
		if (date != null && date.isAfter(context.today())) {
			errors.add(new FieldViolation("date", "is in the future"));
		}

		String description = TextSanitizer.singleLine(raw.get("description"));
		if (description == null) {
			errors.add(new FieldViolation("description", "is required"));
		}
		else if (description.length() > 255) {
			errors.add(new FieldViolation("description", "must be at most 255 characters"));
		}

		BigDecimal amount = null;
		String amountText = raw.get("amount") == null ? null : raw.get("amount").replaceAll("[\\s,₹]", "");
		if (amountText == null || amountText.isEmpty()) {
			errors.add(new FieldViolation("amount", "is required"));
		}
		else if (!AMOUNT.matcher(amountText).matches()) {
			errors.add(new FieldViolation("amount", "must be a number with at most 2 decimal places"));
		}
		else {
			amount = new BigDecimal(amountText);
		}

		TransactionType type = null;
		String typeText = raw.get("type");
		if (typeText == null) {
			if (amount != null && amount.signum() == 0) {
				errors.add(new FieldViolation("amount", "must not be zero"));
			}
			else if (amount != null) {
				type = amount.signum() < 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
				amount = amount.abs();
			}
		}
		else {
			type = switch (typeText.trim().toUpperCase(Locale.ROOT)) {
				case "INCOME", "CREDIT", "CR" -> TransactionType.INCOME;
				case "EXPENSE", "DEBIT", "DR" -> TransactionType.EXPENSE;
				default -> {
					errors.add(new FieldViolation("type", "must be INCOME or EXPENSE"));
					yield null;
				}
			};
			if (amount != null && amount.signum() <= 0) {
				errors.add(new FieldViolation("amount", "must be positive when a type column is given"));
			}
		}

		CurrencyCode currency = context.baseCurrency();
		String currencyText = raw.get("currency");
		if (currencyText != null && !currencyText.trim().equalsIgnoreCase(context.baseCurrency().name())) {
			errors.add(new FieldViolation("currency", "only " + context.baseCurrency() + " is supported"));
		}

		String merchant = TextSanitizer.singleLine(raw.get("merchant"));
		if (merchant != null && merchant.length() > 120) {
			errors.add(new FieldViolation("merchant", "must be at most 120 characters"));
		}
		String notes = TextSanitizer.multiLine(raw.get("notes"));
		if (notes != null && notes.length() > 1000) {
			errors.add(new FieldViolation("notes", "must be at most 1000 characters"));
		}

		String categoryName = TextSanitizer.singleLine(raw.get("category"));
		UUID categoryId = null;
		if (categoryName != null && type != null) {
			categoryId = context.categories()
				.getOrDefault(categoryName.toLowerCase(Locale.ROOT), Map.of())
				.get(type.name());
			if (categoryId == null) {
				warnings.add(new FieldViolation("category",
						"\"" + categoryName + "\" is not one of your " + type.name().toLowerCase(Locale.ROOT)
								+ " categories; the row will be uncategorised"));
			}
		}

		return new ParsedRow(raw.rowNumber(), date, type, amount, currency, description, merchant, categoryName,
				categoryId, notes, List.copyOf(errors), List.copyOf(warnings));
	}

	private static LocalDate parseDate(String text, DateFormatOption format, List<FieldViolation> errors) {
		if (text == null) {
			errors.add(new FieldViolation("date", "is required"));
			return null;
		}
		try {
			return switch (format) {
				case ISO_YYYY_MM_DD -> LocalDate.parse(text.trim());
				case DD_MM_YYYY -> LocalDate.parse(text.trim(), DAY_MONTH_YEAR);
			};
		}
		catch (DateTimeParseException e) {
			errors.add(new FieldViolation("date",
					format == DateFormatOption.ISO_YYYY_MM_DD ? "must be a date like 2026-09-30"
							: "must be a date like 30/09/2026"));
			return null;
		}
	}

}
