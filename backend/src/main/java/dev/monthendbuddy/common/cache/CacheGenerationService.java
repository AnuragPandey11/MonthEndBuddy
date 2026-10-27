package dev.monthendbuddy.common.cache;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.monthendbuddy.common.UserId;

/**
 * Per-user cache generation (architecture D11, §11.1). Every write to a user's transactions, budgets, goals or
 * settings bumps it after commit, which makes every older cache key unreachable at once. A no-op until Redis caching
 * is enabled in P5.2; write services already call {@link #bumpAfterCommit}.
 */
public interface CacheGenerationService {

	long current(UserId user);

	void bump(UserId user);

	/** Bumps once the surrounding transaction commits (immediately when there is none); never on rollback. */
	default void bumpAfterCommit(UserId user) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					bump(user);
				}
			});
		}
		else {
			bump(user);
		}
	}

}
