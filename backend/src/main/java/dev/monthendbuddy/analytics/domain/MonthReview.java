package dev.monthendbuddy.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dev.monthendbuddy.analytics.MonthReviewStatus;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.persistence.UserOwnedEntity;

/** A finished month-end review: what the month kept and what the user did with it. Written once, never changed. */
@Entity
@Table(name = "month_reviews")
public class MonthReview extends UserOwnedEntity {

	@Column(name = "month_start", nullable = false, updatable = false)
	private LocalDate monthStart;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, updatable = false)
	private MonthReviewStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "currency", nullable = false, length = 3, updatable = false)
	private CurrencyCode currency;

	@Column(name = "kept_amount", nullable = false, precision = 19, scale = 4, updatable = false)
	private BigDecimal keptAmount;

	@Column(name = "added_to_savings", precision = 19, scale = 4, updatable = false)
	private BigDecimal addedToSavings;

	@Column(name = "set_aside_for_goals", precision = 19, scale = 4, updatable = false)
	private BigDecimal setAsideForGoals;

	protected MonthReview() {
	}

	/** {@code DONE} when anything was added or set aside, {@code SKIPPED} when the user chose to change nothing. */
	public static MonthReview record(UserId owner, YearMonth month, Money kept, Money addedToSavings,
			Money setAsideForGoals) {
		MonthReview review = new MonthReview();
		review.userId = owner;
		review.monthStart = month.atDay(1);
		review.currency = kept.currency();
		review.keptAmount = kept.amount();
		review.addedToSavings = addedToSavings == null ? null : addedToSavings.amount();
		review.setAsideForGoals = setAsideForGoals == null ? null : setAsideForGoals.amount();
		review.status = addedToSavings == null && setAsideForGoals == null ? MonthReviewStatus.SKIPPED
				: MonthReviewStatus.DONE;
		return review;
	}

	public MonthReviewStatus getStatus() {
		return status;
	}

	public Money getAddedToSavings() {
		return addedToSavings == null ? null : new Money(addedToSavings, currency);
	}

	public Money getSetAsideForGoals() {
		return setAsideForGoals == null ? null : new Money(setAsideForGoals, currency);
	}

}
