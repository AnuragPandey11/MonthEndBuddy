package dev.monthendbuddy.transactions.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.error.FieldViolation;
import dev.monthendbuddy.transactions.DateFormatOption;
import dev.monthendbuddy.transactions.ImportRowStatus;
import dev.monthendbuddy.transactions.ImportStatus;
import dev.monthendbuddy.transactions.TransactionType;

/** What the import endpoints return (LLD §11.3). */
public final class ImportViews {

	private ImportViews() {
	}

	public record Totals(int total, int valid, int invalid, int duplicate) {
	}

	public record RowProblem(int rowNumber, String field, String message) {
	}

	public record ImportResult(UUID jobId, int imported, int skippedDuplicates, int skippedInvalid) {
	}

	/** {@code result} is set once the job is committed; {@code sampleErrors} shows at most 10 problems. */
	public record ImportJobView(UUID id, String filename, ImportStatus status, DateFormatOption dateFormat,
			Totals totals, List<RowProblem> sampleErrors, ImportResult result, String failureReason,
			Instant createdAt) {
	}

	public record ImportRowView(UUID id, int rowNumber, ImportRowStatus status, LocalDate transactionDate,
			TransactionType type, Money amount, String description, String merchant, String categoryName,
			List<FieldViolation> errors, List<FieldViolation> warnings) {
	}

}
