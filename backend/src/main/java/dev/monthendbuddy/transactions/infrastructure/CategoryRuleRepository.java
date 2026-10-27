package dev.monthendbuddy.transactions.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.persistence.UserOwnedRepository;
import dev.monthendbuddy.transactions.domain.CategoryRule;

public interface CategoryRuleRepository extends UserOwnedRepository<CategoryRule> {

	@Query("select r from CategoryRule r where r.userId = :user order by r.priority, r.createdAt, r.id")
	List<CategoryRule> findInPriorityOrder(@Param("user") UserId user);

}
