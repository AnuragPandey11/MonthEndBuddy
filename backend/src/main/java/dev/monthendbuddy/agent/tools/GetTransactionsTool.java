package dev.monthendbuddy.agent.tools;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import dev.monthendbuddy.common.Assumption;
import dev.monthendbuddy.common.AssumptionLabel;
import dev.monthendbuddy.common.Period;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.transactions.CategoryQueries;
import dev.monthendbuddy.transactions.CategoryRef;
import dev.monthendbuddy.transactions.TransactionFilter;
import dev.monthendbuddy.transactions.TransactionQueries;
import dev.monthendbuddy.transactions.TransactionType;
import dev.monthendbuddy.transactions.TransactionView;

/**
 * Same query as {@code GET /api/v1/transactions}, bounded for the model: at most 50 rows (a larger limit is clamped,
 * not rejected), descriptions cut to 120 characters, and {@code truncated} set when more rows match.
 */
@Component
class GetTransactionsTool implements FinanceTool<GetTransactionsTool.TransactionSearchInput> {

	static final int DEFAULT_LIMIT = 20;

	static final int MAX_LIMIT = 50;

	private final TransactionQueries transactions;

	private final CategoryQueries categories;

	private final Clock clock;

	GetTransactionsTool(TransactionQueries transactions, CategoryQueries categories, Clock clock) {
		this.transactions = transactions;
		this.categories = categories;
		this.clock = clock;
	}

	record TransactionSearchInput(
			@JsonProperty(required = false) @JsonPropertyDescription("First day, inclusive, as YYYY-MM-DD. Omit for the start of the current month.") LocalDate from,
			@JsonProperty(required = false) @JsonPropertyDescription("Last day, inclusive, as YYYY-MM-DD. Omit for today.") LocalDate to,
			@JsonProperty(required = false) @JsonPropertyDescription("Category name, e.g. Dining") @Size(max = 60) String category,
			@JsonProperty(required = false) @JsonPropertyDescription("INCOME or EXPENSE") TransactionType type,
			@JsonProperty(required = false) @JsonPropertyDescription("Words to find in the description or merchant") @Size(max = 100) String text,
			@JsonProperty(required = false) @JsonPropertyDescription("Only transactions of at least this amount") @Positive @DecimalMax("1000000000") BigDecimal minAmount,
			@JsonProperty(required = false) @JsonPropertyDescription("Rows to return, newest first; at most 50, default 20") @Min(1) Integer limit) {
	}

	/** {@code truncated} is true when more transactions match than were returned. */
	record TransactionRows(LocalDate from, LocalDate to, List<ToolTransactionRow> rows, boolean truncated) {
	}

	@Override
	public String name() {
		return "getTransactions";
	}

	@Override
	public String description() {
		return "Lists individual transactions, newest first, filtered by date range, category, type, text or "
				+ "minimum amount. Use to show examples or find specific purchases; use the summary tools for totals.";
	}

	@Override
	public Class<TransactionSearchInput> inputType() {
		return TransactionSearchInput.class;
	}

	@Override
	public String statusLabel() {
		return "Looking through your transactions";
	}

	@Override
	public ToolResult execute(TransactionSearchInput input, ToolContext context) {
		List<Assumption> assumptions = new ArrayList<>();
		LocalDate to = input.to() != null ? input.to() : context.today();
		LocalDate from = input.from() != null ? input.from() : context.currentMonth().atDay(1);
		if (input.from() == null) {
			assumptions.add(new Assumption("from", from.toString(), AssumptionLabel.DEFAULT_ASSUMPTION));
		}
		Period period = new Period(from, to);
		int limit = input.limit() == null ? DEFAULT_LIMIT : Math.min(input.limit(), MAX_LIMIT);
		TransactionFilter filter = new TransactionFilter(period.startInclusive(), period.endInclusive(),
				categoryId(input.category(), context), input.type(), Tools.text(input.text()), input.minAmount(), null);
		List<TransactionView> found = transactions.find(context.userId(), filter, limit + 1);
		List<ToolTransactionRow> rows = found.stream().limit(limit).map(ToolTransactionRow::of).toList();
		return ToolResult.ok(new TransactionRows(from, to, rows, found.size() > limit),
				new Provenance(Tools.TRANSACTIONS, from + ".." + to, context.currency(), clock.instant(),
						(long) rows.size()),
				assumptions);
	}

	private UUID categoryId(String name, ToolContext context) {
		if (name == null || name.isBlank()) {
			return null;
		}
		List<CategoryRef> usable = categories.usable(context.userId());
		return usable.stream()
			.filter(c -> c.name().equalsIgnoreCase(name.strip()))
			.findFirst()
			.map(CategoryRef::id)
			.orElseThrow(() -> ValidationException.of("category", "unknown category; use one of: "
					+ String.join(", ", usable.stream().map(CategoryRef::name).map(Tools::text).sorted().toList())));
	}

}
