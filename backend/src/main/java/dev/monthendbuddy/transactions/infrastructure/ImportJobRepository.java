package dev.monthendbuddy.transactions.infrastructure;

import java.time.Instant;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.monthendbuddy.common.persistence.UserOwnedRepository;
import dev.monthendbuddy.transactions.domain.ImportJob;

public interface ImportJobRepository extends UserOwnedRepository<ImportJob> {

	/** Uploads nobody committed within the retention window can no longer be committed: their rows are purged. */
	@Modifying
	@Query("""
			update ImportJob j set j.status = dev.monthendbuddy.transactions.ImportStatus.DISCARDED,
			  j.failureReason = 'Expired before it was committed'
			where j.status in (dev.monthendbuddy.transactions.ImportStatus.VALIDATED,
			                   dev.monthendbuddy.transactions.ImportStatus.FAILED)
			  and j.updatedAt < :cutoff""")
	int expireIdleSince(@Param("cutoff") Instant cutoff);

}
