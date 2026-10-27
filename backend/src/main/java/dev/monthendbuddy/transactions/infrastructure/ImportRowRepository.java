package dev.monthendbuddy.transactions.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.transactions.ImportRowStatus;
import dev.monthendbuddy.transactions.domain.ImportRow;

/** Staged rows; every query is scoped to the user as well as the job. */
public interface ImportRowRepository extends Repository<ImportRow, UUID> {

	<S extends ImportRow> List<S> saveAll(Iterable<S> rows);

	@Query("""
			select r from ImportRow r
			where r.importJobId = :jobId and r.userId = :user and r.status in :statuses
			order by r.rowNumber""")
	List<ImportRow> findByJob(@Param("jobId") UUID jobId, @Param("user") UserId user,
			@Param("statuses") Collection<ImportRowStatus> statuses);

	@Modifying
	@Query("delete from ImportRow r where r.importJobId = :jobId and r.userId = :user")
	int deleteByJob(@Param("jobId") UUID jobId, @Param("user") UserId user);

	/**
	 * Purges the staged rows of every job last touched before {@code cutoff}, committed or not, so raw statement data
	 * is never kept longer than 7 days (architecture §8.4).
	 */
	@Modifying
	@Query("delete from ImportRow r where r.importJobId in (select j.id from ImportJob j where j.updatedAt < :cutoff)")
	int purgeJobsIdleSince(@Param("cutoff") Instant cutoff);

}
