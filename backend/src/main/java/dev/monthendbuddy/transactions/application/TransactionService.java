package dev.monthendbuddy.transactions.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.CursorPage;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.Uuids;
import dev.monthendbuddy.common.cache.CacheGenerationService;
import dev.monthendbuddy.common.error.ConflictException;
import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.common.error.UnsupportedCurrencyException;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.common.events.EventPublisher;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.identity.UserSettingsView;
import dev.monthendbuddy.transactions.CategorySource;
import dev.monthendbuddy.transactions.TransactionFilter;
import dev.monthendbuddy.transactions.TransactionSource;
import dev.monthendbuddy.transactions.TransactionType;
import dev.monthendbuddy.transactions.TransactionView;
import dev.monthendbuddy.transactions.domain.Transaction;
import dev.monthendbuddy.transactions.events.TransactionCreated;
import dev.monthendbuddy.transactions.events.TransactionDeleted;
import dev.monthendbuddy.transactions.events.TransactionUpdated;
import dev.monthendbuddy.transactions.infrastructure.TransactionQueryRepository;
import dev.monthendbuddy.transactions.infrastructure.TransactionRepository;

/**
 * Manual transaction CRUD and listing (architecture §8.1). Every method takes the {@link UserId} first and reaches
 * rows only through user-scoped repositories. Writes publish events (delivered after commit) and bump the user's cache
 * generation after commit.
 */
@Service
public class TransactionService {

	public static final int DEFAULT_PAGE_SIZE = 50;

	public static final int MAX_PAGE_SIZE = 100;

	private final TransactionRepository transactions;

	private final TransactionQueryRepository queries;

	private final CategoryService categories;

	private final Categorizer categorizer;

	private final UserSettingsQuery settings;

	private final EventPublisher events;

	private final CacheGenerationService cacheGeneration;

	private final Clock clock;

	public TransactionService(TransactionRepository transactions, TransactionQueryRepository queries,
			CategoryService categories, Categorizer categorizer, UserSettingsQuery settings, EventPublisher events,
			CacheGenerationService cacheGeneration, Clock clock) {
		this.transactions = transactions;
		this.queries = queries;
		this.categories = categories;
		this.categorizer = categorizer;
		this.settings = settings;
		this.events = events;
		this.cacheGeneration = cacheGeneration;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public TransactionView get(UserId user, UUID id) {
		return queries.findOne(user, id).orElseThrow(() -> new NotFoundException("Transaction"));
	}

	@Transactional(readOnly = true)
	public CursorPage<TransactionView> list(UserId user, TransactionFilter filter, String cursor, Integer limit) {
		int size = limit == null ? DEFAULT_PAGE_SIZE : limit;
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw ValidationException.of("limit", "must be between 1 and " + MAX_PAGE_SIZE);
		}
		List<TransactionView> rows = queries.page(user, filter, TransactionCursor.decode(cursor), size + 1);
		if (rows.size() <= size) {
			return new CursorPage<>(rows, null);
		}
		List<TransactionView> page = rows.subList(0, size);
		return new CursorPage<>(page, TransactionCursor.encode(page.get(size - 1)));
	}

	@Transactional
	public TransactionView create(UserId user, Input input) {
		Transaction transaction = transactions
			.saveAndFlush(Transaction.create(user, validated(user, input, null), TransactionSource.MANUAL));
		events.publish(new TransactionCreated(Uuids.v7(), user, transaction.getId(), clock.instant(),
				transaction.getType().name(), transaction.getAmount(), transaction.getCategoryId(),
				transaction.getTransactionDate()));
		cacheGeneration.bumpAfterCommit(user);
		return get(user, transaction.getId());
	}

	/** @throws ConflictException {@code version_conflict} when {@code expectedVersion} is stale */
	@Transactional
	public TransactionView update(UserId user, UUID id, Input input, long expectedVersion) {
		Transaction transaction = transactions.findByIdAndUserId(id, user)
			.orElseThrow(() -> new NotFoundException("Transaction"));
		if (transaction.getVersion() != expectedVersion) {
			throw ConflictException.versionConflict();
		}
		UUID previousCategory = transaction.getCategoryId();
		LocalDate previousDate = transaction.getTransactionDate();
		transaction.update(validated(user, input, transaction));
		transactions.saveAndFlush(transaction);
		events.publish(new TransactionUpdated(Uuids.v7(), user, id, clock.instant(), transaction.getType().name(),
				transaction.getAmount(), transaction.getCategoryId(), transaction.getTransactionDate(),
				previousCategory, previousDate));
		cacheGeneration.bumpAfterCommit(user);
		return get(user, id);
	}

	@Transactional
	public void delete(UserId user, UUID id) {
		Transaction transaction = transactions.findByIdAndUserId(id, user)
			.orElseThrow(() -> new NotFoundException("Transaction"));
		transactions.delete(transaction);
		events.publish(new TransactionDeleted(Uuids.v7(), user, id, clock.instant(), transaction.getType().name(),
				transaction.getAmount(), transaction.getCategoryId(), transaction.getTransactionDate()));
		cacheGeneration.bumpAfterCommit(user);
	}

	/**
	 * Checks currency, date and category. On update, an unchanged category (same id, same type) is accepted as is,
	 * even if it has since been archived, and keeps its original source; only a newly chosen category is USER-sourced.
	 */
	private Transaction.Details validated(UserId user, Input input, Transaction existing) {
		UserSettingsView userSettings = settings.get(user);
		if (input.amount().currency() != userSettings.baseCurrency()) {
			throw new UnsupportedCurrencyException("amount.currency", userSettings.baseCurrency());
		}
		if (input.transactionDate().isAfter(userSettings.today(clock))) {
			throw ValidationException.of("transactionDate", "must not be in the future");
		}
		if (existing == null && input.categoryId() == null) {
			// No category chosen on create: the user's rules decide (architecture §8.1).
			var ruled = categorizer.categorize(user, input.type(), input.description(), input.merchant());
			if (ruled.isPresent()) {
				return new Transaction.Details(input.type(), input.amount(), input.transactionDate(),
						input.description(), input.merchant(), ruled.get(), CategorySource.RULE, input.notes());
			}
		}
		boolean unchangedCategory = existing != null && input.categoryId() != null
				&& input.categoryId().equals(existing.getCategoryId()) && input.type() == existing.getType();
		CategorySource source = unchangedCategory ? existing.getCategorySource() : CategorySource.USER;
		if (input.categoryId() != null && !unchangedCategory) {
			categories.requireUsable(user, input.categoryId(), input.type());
		}
		return new Transaction.Details(input.type(), input.amount(), input.transactionDate(), input.description(),
				input.merchant(), input.categoryId(), source, input.notes());
	}

	/** What a user enters for a transaction; a category chosen here is always {@code USER}-sourced. */
	public record Input(TransactionType type, Money amount, LocalDate transactionDate, String description,
			String merchant, UUID categoryId, String notes) {
	}

}
