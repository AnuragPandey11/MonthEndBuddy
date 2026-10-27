/**
 * Transactions, categories, category rules and CSV import (architecture §8, LLD §4). Exposes
 * {@code TransactionQueries}, {@code TransactionAggregations} and {@code CategoryQueries} from this package and its
 * domain events from {@code transactions.events}.
 */
@ApplicationModule(displayName = "Transactions", allowedDependencies = { "identity" })
package dev.monthendbuddy.transactions;

import org.springframework.modulith.ApplicationModule;
