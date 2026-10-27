package dev.monthendbuddy.transactions.infrastructure;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.persistence.UserOwnedRepository;
import dev.monthendbuddy.transactions.domain.Transaction;

public interface TransactionRepository extends UserOwnedRepository<Transaction> {

	/** Deletes every transaction of one user. Only {@code TransactionSeedingService} may call this. */
	@Modifying
	@Query("delete from Transaction t where t.userId = :user")
	int deleteAllForDemoReset(@Param("user") UserId user);

}
