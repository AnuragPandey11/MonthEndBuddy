package dev.monthendbuddy.common.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.UserId;

/**
 * The only way to reach user-owned rows (architecture §5.3). It extends the marker {@link Repository}, not
 * {@code JpaRepository}, so unscoped {@code findById} and {@code findAll} do not exist. Module repositories extend
 * this and add only methods that take a {@link UserId}.
 */
@NoRepositoryBean
public interface UserOwnedRepository<T extends UserOwnedEntity> extends Repository<T, UUID> {

	Optional<T> findByIdAndUserId(UUID id, UserId userId);

	List<T> findAllByUserId(UserId userId);

	/** Returns the number of rows deleted: 0 when the row is missing or belongs to another user. */
	@Transactional
	long deleteByIdAndUserId(UUID id, UserId userId);

	<S extends T> S save(S entity);

	/** Saves and flushes, so the returned entity carries its generated id and incremented version. */
	<S extends T> S saveAndFlush(S entity);

	/** Batch insert; Hibernate groups the statements (hibernate.jdbc.batch_size). */
	<S extends T> List<S> saveAll(Iterable<S> entities);

	/** Flushes pending inserts, e.g. between batches of a large import. */
	void flush();

	/** Deletes an entity already loaded through a user-scoped finder. */
	void delete(T entity);

}
