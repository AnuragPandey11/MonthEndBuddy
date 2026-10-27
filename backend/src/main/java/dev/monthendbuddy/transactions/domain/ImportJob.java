package dev.monthendbuddy.transactions.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.IllegalStateTransitionException;
import dev.monthendbuddy.common.persistence.UserOwnedEntity;
import dev.monthendbuddy.transactions.DateFormatOption;
import dev.monthendbuddy.transactions.ImportStatus;

/**
 * One uploaded CSV file and its lifecycle (LLD §4.2): VALIDATED → COMMITTED or DISCARDED; a failed commit is FAILED
 * and may be retried. The entity rejects every other transition.
 */
@Entity
@Table(name = "import_jobs")
public class ImportJob extends UserOwnedEntity {

	@Column(name = "filename", nullable = false)
	private String filename;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private ImportStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "date_format", nullable = false)
	private DateFormatOption dateFormat;

	@Column(name = "total_rows", nullable = false)
	private int totalRows;

	@Column(name = "valid_rows", nullable = false)
	private int validRows;

	@Column(name = "invalid_rows", nullable = false)
	private int invalidRows;

	@Column(name = "duplicate_rows", nullable = false)
	private int duplicateRows;

	@Column(name = "imported_rows")
	private Integer importedRows;

	@Column(name = "skipped_duplicates")
	private Integer skippedDuplicates;

	@Column(name = "skipped_invalid")
	private Integer skippedInvalid;

	@Column(name = "failure_reason")
	private String failureReason;

	@Column(name = "committed_at")
	private Instant committedAt;

	protected ImportJob() {
	}

	public static ImportJob validated(UserId owner, String filename, DateFormatOption dateFormat, int total, int valid,
			int invalid, int duplicate) {
		ImportJob job = new ImportJob();
		job.userId = owner;
		job.filename = filename;
		job.dateFormat = dateFormat;
		job.status = ImportStatus.VALIDATED;
		job.totalRows = total;
		job.validRows = valid;
		job.invalidRows = invalid;
		job.duplicateRows = duplicate;
		return job;
	}

	public boolean isCommittable() {
		return status == ImportStatus.VALIDATED || status == ImportStatus.FAILED;
	}

	public void markCommitted(Instant at, int imported, int skippedDuplicatesCount, int skippedInvalidCount) {
		requireCommittable("commit");
		this.status = ImportStatus.COMMITTED;
		this.committedAt = at;
		this.importedRows = imported;
		this.skippedDuplicates = skippedDuplicatesCount;
		this.skippedInvalid = skippedInvalidCount;
		this.failureReason = null;
	}

	public void discard() {
		requireCommittable("discard");
		this.status = ImportStatus.DISCARDED;
	}

	public void fail(String reason) {
		requireCommittable("fail");
		this.status = ImportStatus.FAILED;
		this.failureReason = reason;
	}

	private void requireCommittable(String action) {
		if (!isCommittable()) {
			throw new IllegalStateTransitionException("Cannot " + action + " an import that is " + status);
		}
	}

	public String getFilename() {
		return filename;
	}

	public ImportStatus getStatus() {
		return status;
	}

	public DateFormatOption getDateFormat() {
		return dateFormat;
	}

	public int getTotalRows() {
		return totalRows;
	}

	public int getValidRows() {
		return validRows;
	}

	public int getInvalidRows() {
		return invalidRows;
	}

	public int getDuplicateRows() {
		return duplicateRows;
	}

	public Integer getImportedRows() {
		return importedRows;
	}

	public Integer getSkippedDuplicates() {
		return skippedDuplicates;
	}

	public Integer getSkippedInvalid() {
		return skippedInvalid;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public Instant getCommittedAt() {
		return committedAt;
	}

}
