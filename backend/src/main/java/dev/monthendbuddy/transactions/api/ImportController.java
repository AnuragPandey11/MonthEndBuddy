package dev.monthendbuddy.transactions.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.monthendbuddy.common.error.ConflictException;
import dev.monthendbuddy.common.error.MonthEndBuddyException;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.common.ratelimit.RateLimitBucket;
import dev.monthendbuddy.common.ratelimit.RateLimiter;
import dev.monthendbuddy.identity.CurrentUser;
import dev.monthendbuddy.transactions.DateFormatOption;
import dev.monthendbuddy.transactions.ImportRowStatus;
import dev.monthendbuddy.transactions.application.ImportService;
import dev.monthendbuddy.transactions.application.ImportViews.ImportJobView;
import dev.monthendbuddy.transactions.application.ImportViews.ImportResult;
import dev.monthendbuddy.transactions.application.ImportViews.ImportRowView;

/** CSV import: upload → preview (rows, errors, duplicates) → commit or discard (architecture §8.4). */
@RestController
@RequestMapping("/api/v1/imports")
class ImportController {

	/** Browsers and spreadsheet tools label CSV files inconsistently. */
	private static final Set<String> CSV_TYPES = Set.of("text/csv", "application/csv", "text/plain",
			"application/vnd.ms-excel", "application/octet-stream");

	private final ImportService imports;

	private final RateLimiter rateLimiter;

	ImportController(ImportService imports, RateLimiter rateLimiter) {
		this.imports = imports;
		this.rateLimiter = rateLimiter;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<ImportJobView> upload(CurrentUser user, @RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "ISO_YYYY_MM_DD") DateFormatOption dateFormat) throws IOException {
		rateLimiter.consumeOrThrow(RateLimitBucket.CSV_IMPORT, user.id().toString());
		String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		String type = file.getContentType() == null ? "application/octet-stream"
				: file.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
		if (file.isEmpty()) {
			throw ValidationException.of("file", "is empty");
		}
		if (!name.toLowerCase(Locale.ROOT).endsWith(".csv") || !CSV_TYPES.contains(type)) {
			throw ValidationException.of("file", "must be a .csv file");
		}
		try (InputStream content = file.getInputStream()) {
			ImportJobView job = imports.upload(user.id(), name, content, dateFormat);
			return ResponseEntity.created(URI.create("/api/v1/imports/" + job.id())).body(job);
		}
	}

	@GetMapping("/{id}")
	ImportJobView get(CurrentUser user, @PathVariable UUID id) {
		return imports.get(user.id(), id);
	}

	@GetMapping("/{id}/rows")
	List<ImportRowView> rows(CurrentUser user, @PathVariable UUID id,
			@RequestParam(required = false) ImportRowStatus status) {
		return imports.rows(user.id(), id, status);
	}

	/**
	 * A failed commit is recorded as FAILED in its own transaction and can be retried. That call is made here, from
	 * outside {@code ImportService}, so its {@code REQUIRES_NEW} goes through the Spring proxy (plan P2.4).
	 */
	@PostMapping("/{id}/commit")
	ImportResult commit(CurrentUser user, @PathVariable UUID id, @RequestBody(required = false) CommitRequest body) {
		try {
			return imports.commit(user.id(), id, body == null ? Set.of() : body.forceRowIdsOrEmpty());
		}
		catch (DataIntegrityViolationException e) {
			imports.markFailed(user.id(), id, "Some rows were imported by another request at the same time");
			throw new ConflictException("import_conflict",
					"Some rows were imported by another request at the same time; commit again to skip them");
		}
		catch (MonthEndBuddyException e) {
			throw e;
		}
		catch (RuntimeException e) {
			imports.markFailed(user.id(), id, "The import could not be saved");
			throw e;
		}
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> discard(CurrentUser user, @PathVariable UUID id) {
		imports.discard(user.id(), id);
		return ResponseEntity.noContent().build();
	}

	record CommitRequest(Set<UUID> forceRowIds) {

		Set<UUID> forceRowIdsOrEmpty() {
			return forceRowIds == null ? Set.of() : forceRowIds;
		}

	}

}
