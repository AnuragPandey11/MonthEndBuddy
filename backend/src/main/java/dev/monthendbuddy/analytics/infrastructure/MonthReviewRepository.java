package dev.monthendbuddy.analytics.infrastructure;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.monthendbuddy.analytics.domain.MonthReview;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.persistence.UserOwnedRepository;

public interface MonthReviewRepository extends UserOwnedRepository<MonthReview> {

	Optional<MonthReview> findByUserIdAndMonthStart(UserId userId, LocalDate monthStart);

	@Modifying
	@Query("delete from MonthReview r where r.userId = :user")
	int deleteAllForDemoReset(@Param("user") UserId user);

}
