package dev.monthendbuddy.transactions.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.TextSanitizer;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.common.persistence.UserOwnedEntity;
import dev.monthendbuddy.transactions.CategorySource;
import dev.monthendbuddy.transactions.TransactionSource;
import dev.monthendbuddy.transactions.TransactionType;

/** One income or expense. The amount is always positive; {@link #getType()} carries the sign (architecture §6.1). */
@Entity
@Table(name = "transactions")
public class Transaction extends UserOwnedEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false)
	private TransactionType type;

	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(name = "currency", nullable = false, length = 3)
	private CurrencyCode currency;

	@Column(name = "transaction_date", nullable = false)
	private LocalDate transactionDate;

	@Column(name = "description", nullable = false)
	private String description;

	@Column(name = "merchant")
	private String merchant;

	@Column(name = "category_id")
	private UUID categoryId;

	@Enumerated(EnumType.STRING)
	@Column(name = "category_source", nullable = false)
	private CategorySource categorySource;

	@Column(name = "notes")
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, updatable = false)
	private TransactionSource source;

	@Column(name = "import_job_id", updatable = false)
	private UUID importJobId;

	@Column(name = "fingerprint")
	private String fingerprint;

	@Column(name = "fingerprint_seq")
	private Integer fingerprintSeq;

	protected Transaction() {
	}

	public static Transaction create(UserId owner, Details details, TransactionSource source) {
		Transaction transaction = new Transaction();
		transaction.userId = owner;
		transaction.source = source;
		transaction.apply(details);
		return transaction;
	}

	/**
	 * A row committed from a CSV import. {@code fingerprint} is {@code null} for a duplicate the user chose to import
	 * anyway, so the duplicate index does not cover it (architecture §8.3).
	 */
	public static Transaction imported(UserId owner, Details details, UUID importJobId, String fingerprint,
			Integer fingerprintSeq) {
		Transaction transaction = create(owner, details, TransactionSource.CSV_IMPORT);
		transaction.importJobId = importJobId;
		transaction.fingerprint = fingerprint;
		transaction.fingerprintSeq = fingerprint == null ? null : fingerprintSeq;
		return transaction;
	}

	public void update(Details details) {
		apply(details);
	}

	private void apply(Details details) {
		if (!details.amount().isPositive()) {
			throw ValidationException.of("amount", "must be greater than zero");
		}
		String cleanDescription = TextSanitizer.singleLine(details.description());
		if (cleanDescription == null) {
			throw ValidationException.of("description", "must not be blank");
		}
		this.type = details.type();
		this.amount = details.amount().amount();
		this.currency = details.amount().currency();
		this.transactionDate = details.transactionDate();
		this.description = cleanDescription;
		this.merchant = TextSanitizer.singleLine(details.merchant());
		this.categoryId = details.categoryId();
		this.categorySource = details.categoryId() == null ? CategorySource.NONE : details.categorySource();
		this.notes = TextSanitizer.multiLine(details.notes());
	}

	public boolean isExpense() {
		return type == TransactionType.EXPENSE;
	}

	public TransactionType getType() {
		return type;
	}

	public Money getAmount() {
		return new Money(amount, currency);
	}

	public LocalDate getTransactionDate() {
		return transactionDate;
	}

	public UUID getCategoryId() {
		return categoryId;
	}

	public CategorySource getCategorySource() {
		return categorySource;
	}

	/** Fields a user (or an import) sets; {@code categorySource} is ignored when {@code categoryId} is null. */
	public record Details(TransactionType type, Money amount, LocalDate transactionDate, String description,
			String merchant, UUID categoryId, CategorySource categorySource, String notes) {
	}

}
