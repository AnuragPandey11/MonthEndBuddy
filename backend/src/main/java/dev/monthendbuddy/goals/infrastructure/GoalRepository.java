package dev.monthendbuddy.goals.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.persistence.UserOwnedRepository;
import dev.monthendbuddy.goals.domain.SavingsGoal;

public interface GoalRepository extends UserOwnedRepository<SavingsGoal> {

	@Query("""
			select g from SavingsGoal g
			where g.userId = :user and g.status <> dev.monthendbuddy.goals.GoalStatus.ARCHIVED
			order by g.priority, g.targetDate, g.createdAt""")
	List<SavingsGoal> findCurrent(@Param("user") UserId user);

	@Modifying
	@Query("delete from SavingsGoal g where g.userId = :user")
	int deleteAllForDemoReset(@Param("user") UserId user);

}
