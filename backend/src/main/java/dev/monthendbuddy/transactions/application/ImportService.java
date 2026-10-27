package dev.monthendbuddy.transactions.application;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.Uuids;
import dev.monthendbuddy.common.cache.CacheGenerationService;
import dev.monthendbuddy.common.error.FieldViolation;
import dev.monthendbuddy.common.error.IllegalStateTransitionException;
import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.common.events.EventPublisher;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.identity.UserSettingsView;
import dev.monthendbuddy.transactions.CategorySource;
import dev.monthendbuddy.transactions.DateFormatOption;
import dev.monthendbuddy.transactions.ImportRowStatus;
import dev.monthendbuddy.transactions.ImportStatus;
import dev.monthendbuddy.transactions.application.ImportViews.ImportJobView;
import dev.monthendbuddy.transactions.application.ImportViews.ImportResult;
import dev.monthendbuddy.transactions.application.ImportViews.ImportRowView;
import dev.monthendbuddy.transactions.application.ImportViews.RowProblem;
import dev.monthendbuddy.transactions.application.RowValidator.ParsedRow;
import dev.monthendbuddy.transactions.domain.Category;
import dev.monthendbuddy.transactions.domain.ImportJob;
import dev.monthendbuddy.transactions.domain.ImportRow;
import dev.monthendbuddy.transactions.domain.Transaction;
import dev.monthendbuddy.transactions.events.TransactionsImported;
import dev.monthendbuddy.transactions.infrastructure.CategoryRepository;
import dev.monthendbuddy.transactions.infrastructure.CsvTransactionParser;
import dev.monthendbuddy.transactions.infrastructure.CsvTransactionParser.RawCsvRow;
import dev.monthendbuddy.transactions.infrastructure.ImportJobRepository;
import dev.monthendbuddy.transactions.infrastructure.ImportRowRepository;
import dev.monthendbuddy.transactions.infrastructure.TransactionQueryRepository;
import dev.monthendbuddy.transactions.infrastructure.TransactionQueryRepository.FingerprintKey;
import dev.monthendbuddy.transactions.infrastructure.TransactionRepository;

/**
 * Two-step CSV import (architecture D14, §8.4, LLD §4.2). {@link #upload} parses, validates and stages every row and
 * reports duplicates; nothing touches real transactions until {@link #commit}, which is atomic and idempotent.
 */
@Service
public class ImportService {

	public static final int INSERT_BATCH = 500;

	public static final Duration RETENTION = Duration.ofDays(7);

	private static final int SAMPLE_ERRORS = 10;

	private static final TypeReference<List<FieldViolation>> VIOLATIONS = new TypeReference<>() {
	};

	private static final Logger log = LoggerFactory.getLogger(ImportService.class);

	private final CsvTransactionParser parser;

	private final RowValidator validator;

	private final FingerprintService fingerprints;

	private final ImportJobRepository jobs;

	private final ImportRowRepository rows;

	private final TransactionRepository transactions;

	private final TransactionQueryRepository queries;

	private final CategoryRepository categories;

	private final Categorizer categorizer;

	private final UserSettingsQuery settings;

	private final EventPublisher events;

	private final CacheGenerationService cacheGeneration;

	private final JsonMapper json;

	private final Clock clock;

	@SuppressWarnings("java:S107")
	public ImportService(CsvTransactionParser parser, RowValidator validator, FingerprintService fingerprints,
			ImportJobRepository jobs, ImportRowRepository rows, TransactionRepository transactions,
			TransactionQueryRepository queries, CategoryRepository categories, Categorizer categorizer,
			UserSettingsQuery settings, EventPublisher events, CacheGenerationService cacheGeneration, JsonMapper json,
			Clock clock) {
		this.parser = parser;
		this.validator = validator;
		this.fingerprints = fingerprints;
		this.jobs = jobs;
		this.rows = rows;
		this.transactions = transactions;
		this.queries = queries;
		this.categories = categories;
		this.categorizer = categorizer;
		this.settings = settings;
		this.events = events;
		this.cacheGeneration = cacheGeneration;
		this.json = json;
		this.clock = clock;
	}

	@Transactional
	public ImportJobView upload(UserId user, String filename, InputStream content, DateFormatOption dateFormat) {
		UserSettingsView userSettings = settings.get(user);
		RowValidator.Context context = new RowValidator.Context(dateFormat, userSettings.today(clock),
				userSettings.baseCurrency(), categoriesByName(user));
		List<RawCsvRow> raw = parser.parse(content);
		if (raw.isEmpty()) {
			throw ValidationException.of("file", "has a header but no rows");
		}
		List<ParsedRow> parsed = raw.stream().map(r -> validator.validate(r, context)).toList();

		// Fingerprint valid rows in file order; equal fingerprints get sequence numbers 1, 2, 3…
		Map<Integer, String> fingerprintByRow = new HashMap<>();
		Map<Integer, Integer> seqByRow = new HashMap<>();
		Map<String, Integer> seen = new HashMap<>();
		for (ParsedRow row : parsed) {
			if (row.isValid()) {
				String fingerprint = fingerprints.fingerprint(user, row.date(), row.type(), row.amount(),
						row.description());
				fingerprintByRow.put(row.rowNumber(), fingerprint);
				seqByRow.put(row.rowNumber(), seen.merge(fingerprint, 1, Integer::sum));
			}
		}
		Set<FingerprintKey> existing = queries.existingFingerprints(user, fingerprintByRow.values());

		int valid = 0;
		int invalid = 0;
		int duplicate = 0;
		List<ParsedRow> staged = new ArrayList<>();
		List<ImportRowStatus> statuses = new ArrayList<>();
		for (ParsedRow row : parsed) {
			ImportRowStatus status;
			if (!row.isValid()) {
				status = ImportRowStatus.INVALID;
				invalid++;
			}
			else if (existing.contains(new FingerprintKey(fingerprintByRow.get(row.rowNumber()),
					seqByRow.get(row.rowNumber())))) {
				status = ImportRowStatus.DUPLICATE;
				duplicate++;
			}
			else {
				status = ImportRowStatus.VALID;
				valid++;
			}
			staged.add(row);
			statuses.add(status);
		}

		ImportJob job = jobs.saveAndFlush(ImportJob.validated(user, safeFilename(filename), dateFormat, parsed.size(),
				valid, invalid, duplicate));
		Instant now = clock.instant();
		List<ImportRow> entities = new ArrayList<>(staged.size());
		for (int i = 0; i < staged.size(); i++) {
			ParsedRow row = staged.get(i);
			entities.add(new ImportRow(job.getId(), user, row.rowNumber(), statuses.get(i), row.date(), row.type(),
					row.amount(), row.currency(), row.description(), row.merchant(), row.categoryName(),
					row.categoryId(), row.notes(), fingerprintByRow.get(row.rowNumber()), seqByRow.get(row.rowNumber()),
					json.writeValueAsString(row.errors()), json.writeValueAsString(row.warnings()), now));
		}
		rows.saveAll(entities);
		return view(job, sampleErrors(staged));
	}

	@Transactional(readOnly = true)
	public ImportJobView get(UserId user, UUID jobId) {
		ImportJob job = requireJob(user, jobId);
		List<ImportRow> problems = rows.findByJob(jobId, user, List.of(ImportRowStatus.INVALID));
		List<RowProblem> sample = new ArrayList<>();
		for (ImportRow row : problems) {
			for (FieldViolation error : read(row.getErrorsJson())) {
				if (sample.size() < SAMPLE_ERRORS) {
					sample.add(new RowProblem(row.getRowNumber(), error.field(), error.message()));
				}
			}
		}
		return view(job, sample);
	}

	@Transactional(readOnly = true)
	public List<ImportRowView> rows(UserId user, UUID jobId, ImportRowStatus status) {
		requireJob(user, jobId);
		List<ImportRowStatus> statuses = status == null ? List.of(ImportRowStatus.values()) : List.of(status);
		return rows.findByJob(jobId, user, statuses)
			.stream()
			.map(r -> new ImportRowView(r.getId(), r.getRowNumber(), r.getStatus(), r.getTransactionDate(),
					r.getType(), r.getAmount() == null ? null : new Money(r.getAmount(), r.getCurrency()),
					r.getDescription(), r.getMerchant(), r.getCategoryName(), read(r.getErrorsJson()),
					read(r.getWarningsJson())))
			.toList();
	}

	/**
	 * Inserts the valid rows plus any duplicates the user chose to import anyway, all or nothing (LLD §4.2).
	 * Committing an already committed job returns its original result.
	 */
	@Transactional
	public ImportResult commit(UserId user, UUID jobId, Set<UUID> forceRowIds) {
		ImportJob job = requireJob(user, jobId);
		if (job.getStatus() == ImportStatus.COMMITTED) {
			return result(job);
		}
		if (!job.isCommittable()) {
			throw new IllegalStateTransitionException("Cannot commit an import that is " + job.getStatus());
		}
		Set<UUID> forced = forceRowIds == null ? Set.of() : forceRowIds;
		List<ImportRow> candidates = rows.findByJob(jobId, user,
				List.of(ImportRowStatus.VALID, ImportRowStatus.DUPLICATE));
		Set<UUID> duplicateIds = new HashSet<>();
		candidates.stream().filter(r -> r.getStatus() == ImportRowStatus.DUPLICATE).forEach(r -> duplicateIds.add(r.getId()));
		if (!duplicateIds.containsAll(forced)) {
			throw ValidationException.of("forceRowIds", "may only list duplicate rows of this import");
		}

		// Another import may have added the same rows since the upload: re-check, and skip those too.
		Set<FingerprintKey> nowExisting = queries.existingFingerprints(user, candidates.stream()
			.filter(r -> r.getStatus() == ImportRowStatus.VALID)
			.map(ImportRow::getFingerprint)
			.toList());

		Categorizer.Snapshot rules = categorizer.forUser(user);
		List<Transaction> batch = new ArrayList<>(INSERT_BATCH);
		int imported = 0;
		int skippedDuplicates = 0;
		LocalDate first = null;
		LocalDate last = null;
		for (ImportRow row : candidates) {
			boolean force = forced.contains(row.getId());
			boolean isNew = row.getStatus() == ImportRowStatus.VALID
					&& !nowExisting.contains(new FingerprintKey(row.getFingerprint(), row.getFingerprintSeq()));
			if (!force && !isNew) {
				skippedDuplicates++;
				continue;
			}
			batch.add(Transaction.imported(user, details(row, rules), jobId, force ? null : row.getFingerprint(),
					row.getFingerprintSeq()));
			imported++;
			first = first == null || row.getTransactionDate().isBefore(first) ? row.getTransactionDate() : first;
			last = last == null || row.getTransactionDate().isAfter(last) ? row.getTransactionDate() : last;
			if (batch.size() == INSERT_BATCH) {
				transactions.saveAll(batch);
				transactions.flush();
				batch.clear();
			}
		}
		transactions.saveAll(batch);
		transactions.flush();

		job.markCommitted(clock.instant(), imported, skippedDuplicates, job.getInvalidRows());
		jobs.saveAndFlush(job);
		if (imported > 0) {
			events.publish(new TransactionsImported(Uuids.v7(), user, jobId, clock.instant(), imported, first, last));
			cacheGeneration.bumpAfterCommit(user);
		}
		return result(job);
	}

	/** Records a failed commit in its own transaction, because the commit's transaction has rolled back. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markFailed(UserId user, UUID jobId, String reason) {
		jobs.findByIdAndUserId(jobId, user).filter(ImportJob::isCommittable).ifPresent(job -> {
			job.fail(reason);
			jobs.saveAndFlush(job);
		});
	}

	@Transactional
	public void discard(UserId user, UUID jobId) {
		ImportJob job = requireJob(user, jobId);
		job.discard();
		jobs.saveAndFlush(job);
		rows.deleteByJob(jobId, user);
	}

	/** Raw statement rows are kept at most 7 days (architecture §8.4); idle uploads expire with them. */
	@Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
	@Transactional
	public int purgeStaleRows() {
		Instant cutoff = clock.instant().minus(RETENTION);
		int purged = rows.purgeJobsIdleSince(cutoff);
		int expired = jobs.expireIdleSince(cutoff);
		if (purged > 0 || expired > 0) {
			log.info("Purged {} staged import rows; expired {} idle imports", purged, expired);
		}
		return purged;
	}

	private Transaction.Details details(ImportRow row, Categorizer.Snapshot rules) {
		UUID categoryId = row.getCategoryId();
		CategorySource source = CategorySource.IMPORT;
		if (categoryId == null) {
			Optional<UUID> ruled = rules.categorize(row.getType(), row.getDescription(), row.getMerchant());
			categoryId = ruled.orElse(null);
			source = ruled.isPresent() ? CategorySource.RULE : CategorySource.NONE;
		}
		return new Transaction.Details(row.getType(), new Money(row.getAmount(), row.getCurrency()),
				row.getTransactionDate(), row.getDescription(), row.getMerchant(), categoryId, source, row.getNotes());
	}

	private Map<String, Map<String, UUID>> categoriesByName(UserId user) {
		Map<String, Map<String, UUID>> byName = new HashMap<>();
		for (Category category : categories.findUsable(user)) {
			byName.computeIfAbsent(category.getName().toLowerCase(Locale.ROOT), k -> new HashMap<>())
				.put(category.getKind().name(), category.getId());
		}
		return byName;
	}

	private ImportJob requireJob(UserId user, UUID jobId) {
		return jobs.findByIdAndUserId(jobId, user).orElseThrow(() -> new NotFoundException("Import"));
	}

	private List<RowProblem> sampleErrors(List<ParsedRow> parsed) {
		return parsed.stream()
			.sorted(Comparator.comparingInt(ParsedRow::rowNumber))
			.flatMap(r -> r.errors().stream().map(e -> new RowProblem(r.rowNumber(), e.field(), e.message())))
			.limit(SAMPLE_ERRORS)
			.toList();
	}

	private List<FieldViolation> read(String jsonArray) {
		return json.readValue(jsonArray, VIOLATIONS);
	}

	private static ImportResult result(ImportJob job) {
		return new ImportResult(job.getId(), job.getImportedRows(), job.getSkippedDuplicates(), job.getSkippedInvalid());
	}

	private static ImportJobView view(ImportJob job, List<RowProblem> sampleErrors) {
		return new ImportJobView(job.getId(), job.getFilename(), job.getStatus(), job.getDateFormat(),
				new ImportViews.Totals(job.getTotalRows(), job.getValidRows(), job.getInvalidRows(),
						job.getDuplicateRows()),
				sampleErrors, job.getStatus() == ImportStatus.COMMITTED ? result(job) : null, job.getFailureReason(),
				job.getCreatedAt());
	}

	/** Keeps only the base name, trimmed, so a client cannot store a path or an absurdly long name. */
	private static String safeFilename(String filename) {
		String name = filename == null ? "statement.csv" : filename.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).strip();
		return name.isEmpty() ? "statement.csv" : name.substring(0, Math.min(name.length(), 200));
	}

}
