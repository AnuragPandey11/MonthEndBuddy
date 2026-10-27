package dev.monthendbuddy.transactions.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.transactions.ImportRowStatus;
import dev.monthendbuddy.transactions.TransactionType;

/** One staged CSV row: its parsed fields (when valid), its status, and row-level errors and warnings as JSON. */
@Entity
@Table(name = "import_rows")
public class ImportRow {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "import_job_id", nullable = false, updatable = false)
	private UUID importJobId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UserId userId;

	@Column(name = "row_number", nullable = false)
	private int rowNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private ImportRowStatus status;

	@Column(name = "transaction_date")
	private LocalDate transactionDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "type")
	private TransactionType type;

	@Column(name = "amount", precision = 19, scale = 4)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(name = "currency", length = 3)
	private CurrencyCode currency;

	@Column(name = "description")
	private String description;

	@Column(name = "merchant")
	private String merchant;

	@Column(name = "category_name")
	private String categoryName;

	@Column(name = "category_id")
	private UUID categoryId;

	@Column(name = "notes")
	private String notes;

	@Column(name = "fingerprint")
	private String fingerprint;

	@Column(name = "fingerprint_seq")
	private Integer fingerprintSeq;

	@Column(name = "errors", nullable = false)
	private String errorsJson;

	@Column(name = "warnings", nullable = false)
	private String warningsJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ImportRow() {
	}

	@SuppressWarnings("java:S107")
	public ImportRow(UUID importJobId, UserId userId, int rowNumber, ImportRowStatus status, LocalDate transactionDate,
			TransactionType type, BigDecimal amount, CurrencyCode currency, String description, String merchant,
			String categoryName, UUID categoryId, String notes, String fingerprint, Integer fingerprintSeq,
			String errorsJson, String warningsJson, Instant createdAt) {
		this.id = UUID.randomUUID();
		this.importJobId = importJobId;
		this.userId = userId;
		this.rowNumber = rowNumber;
		this.status = status;
		this.transactionDate = transactionDate;
		this.type = type;
		this.amount = amount;
		this.currency = currency;
		this.description = description;
		this.merchant = merchant;
		this.categoryName = categoryName;
		this.categoryId = categoryId;
		this.notes = notes;
		this.fingerprint = fingerprint;
		this.fingerprintSeq = fingerprintSeq;
		this.errorsJson = errorsJson;
		this.warningsJson = warningsJson;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public int getRowNumber() {
		return rowNumber;
	}

	public ImportRowStatus getStatus() {
		return status;
	}

	public LocalDate getTransactionDate() {
		return transactionDate;
	}

	public TransactionType getType() {
		return type;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public CurrencyCode getCurrency() {
		return currency;
	}

	public String getDescription() {
		return description;
	}

	public String getMerchant() {
		return merchant;
	}

	public String getCategoryName() {
		return categoryName;
	}

	public UUID getCategoryId() {
		return categoryId;
	}

	public String getNotes() {
		return notes;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	public Integer getFingerprintSeq() {
		return fingerprintSeq;
	}

	public String getErrorsJson() {
		return errorsJson;
	}

	public String getWarningsJson() {
		return warningsJson;
	}

}
