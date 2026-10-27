package dev.monthendbuddy.transactions.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.Uuids;
import dev.monthendbuddy.common.events.EventPublisher;
import dev.monthendbuddy.common.cache.CacheGenerationService;
import dev.monthendbuddy.transactions.CategorySource;
import dev.monthendbuddy.transactions.SeedTransaction;
import dev.monthendbuddy.transactions.TransactionSeeder;
import dev.monthendbuddy.transactions.TransactionSource;
import dev.monthendbuddy.transactions.domain.Category;
import dev.monthendbuddy.transactions.domain.Transaction;
import dev.monthendbuddy.transactions.events.TransactionsImported;
import dev.monthendbuddy.transactions.infrastructure.CategoryRepository;
import dev.monthendbuddy.transactions.infrastructure.TransactionRepository;

/**
 * Bulk replacement for the demo account. Instead of one event per row it publishes a single
 * {@link TransactionsImported} for the seeded date range, so anomaly rules and budgets run once over the new data.
 */
@Service
class TransactionSeedingService implements TransactionSeeder {

	private final TransactionRepository transactions;

	private final CategoryRepository categories;

	private final CacheGenerationService cacheGeneration;

	private final EventPublisher events;

	private final Clock clock;

	TransactionSeedingService(TransactionRepository transactions, CategoryRepository categories,
			CacheGenerationService cacheGeneration, EventPublisher events, Clock clock) {
		this.transactions = transactions;
		this.categories = categories;
		this.cacheGeneration = cacheGeneration;
		this.events = events;
		this.clock = clock;
	}

	@Override
	@Transactional
	public int reseed(UserId demoUser, List<SeedTransaction> rows) {
		transactions.deleteAllForDemoReset(demoUser);
		categories.deleteCustomForDemoReset(demoUser);
		Map<String, Category> system = categories.findSystem()
			.stream()
			.collect(Collectors.toMap(c -> c.getName().toLowerCase(Locale.ROOT), Function.identity()));
		List<Transaction> entities = rows.stream().map(row -> {
			Category category = system.get(row.categoryName().toLowerCase(Locale.ROOT));
			if (category == null || !category.appliesTo(row.type())) {
				throw new IllegalArgumentException("No system " + row.type() + " category named " + row.categoryName());
			}
			return Transaction.create(demoUser, new Transaction.Details(row.type(), row.amount(), row.transactionDate(),
					row.description(), row.merchant(), category.getId(), CategorySource.IMPORT, null),
					TransactionSource.DEMO_SEED);
		}).toList();
		int inserted = transactions.saveAll(entities).size();
		if (inserted > 0) {
			LocalDate first = rows.stream().map(SeedTransaction::transactionDate).min(Comparator.naturalOrder()).orElseThrow();
			LocalDate last = rows.stream().map(SeedTransaction::transactionDate).max(Comparator.naturalOrder()).orElseThrow();
			events.publish(new TransactionsImported(Uuids.v7(), demoUser, demoUser.value(), clock.instant(), inserted,
					first, last));
		}
		cacheGeneration.bumpAfterCommit(demoUser);
		return inserted;
	}

}
