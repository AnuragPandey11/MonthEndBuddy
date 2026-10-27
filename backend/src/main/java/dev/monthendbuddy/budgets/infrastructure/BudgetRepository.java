package dev.monthendbuddy.budgets.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.monthendbuddy.budgets.domain.Budget;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.persistence.UserOwnedRepository;

public interface BudgetRepository extends UserOwnedRepository<Budget> {

	/** The overall budget first, then category budgets in creation order. */
	@Query("""
			select b from Budget b where b.userId = :user and b.active = true
			order by case when b.categoryId is null then 0 else 1 end, b.createdAt, b.id""")
	List<Budget> findActive(@Param("user") UserId user);

	@Modifying
	@Query("delete from Budget b where b.userId = :user")
	int deleteAllForDemoReset(@Param("user") UserId user);

}
