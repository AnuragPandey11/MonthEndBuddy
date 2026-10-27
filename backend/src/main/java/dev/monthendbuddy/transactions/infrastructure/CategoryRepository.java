package dev.monthendbuddy.transactions.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.transactions.domain.Category;

/** Categories are partly shared (system rows), so every query here is "system or owned by {@code user}". */
public interface CategoryRepository extends Repository<Category, UUID> {

	@Query("select c from Category c where c.id = :id and (c.userId is null or c.userId = :user)")
	Optional<Category> findVisible(@Param("id") UUID id, @Param("user") UserId user);

	@Query("""
			select c from Category c
			where (c.userId is null or c.userId = :user) and c.archived = false
			order by c.kind, lower(c.name)""")
	List<Category> findUsable(@Param("user") UserId user);

	/** Includes archived categories, whose names stay reserved, and system names. */
	@Query("""
			select count(c) > 0 from Category c
			where lower(c.name) = lower(:name) and (c.userId is null or c.userId = :user) and c.id <> :excludeId""")
	boolean isNameTaken(@Param("name") String name, @Param("user") UserId user, @Param("excludeId") UUID excludeId);

	Category saveAndFlush(Category category);

	@Query("select c from Category c where c.userId is null")
	List<Category> findSystem();

	/** Deletes one user's custom categories. Only {@code TransactionSeedingService} may call this. */
	@Modifying
	@Query("delete from Category c where c.userId = :user")
	int deleteCustomForDemoReset(@Param("user") UserId user);

}
