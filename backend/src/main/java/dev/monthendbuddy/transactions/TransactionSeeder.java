package dev.monthendbuddy.transactions;

import java.util.List;

import dev.monthendbuddy.common.UserId;

/**
 * Replaces all of a user's transactions and custom categories with synthetic rows ({@code DEMO_SEED}). Destructive by
 * design: the demo module calls it only for the demo account, after identity has confirmed the account is a demo one.
 */
public interface TransactionSeeder {

	/** @return the number of transactions inserted */
	int reseed(UserId demoUser, List<SeedTransaction> rows);

}
