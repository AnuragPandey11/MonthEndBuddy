package dev.monthendbuddy.budgets.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.common.persistence.UserOwnedEntity;

/**
 * A recurring monthly spending limit: overall ({@code categoryId == null}) or for one expense category
 * (architecture §9.2). It applies to every calendar month while active; per-month overrides are out of scope.
 */
@Entity
@Table(name = "budgets")
public class Budget extends UserOwnedEntity {

	public static final List<Integer> DEFAULT_THRESHOLDS = List.of(80, 100);

	@Column(name = "category_id", updatable = false)
	private UUID categoryId;

	@Column(name = "period", nullable = false)
	private String period;

	@Column(name = "limit_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal limitAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "currency", nullable = false, length = 3)
	private CurrencyCode currency;

	@Column(name = "alert_thresholds", nullable = false)
	private Integer[] alertThresholds;

	@Column(name = "active", nullable = false)
	private boolean active;

	protected Budget() {
	}

	public static Budget create(UserId owner, UUID categoryId, Money limit, List<Integer> thresholds) {
		Budget budget = new Budget();
		budget.userId = owner;
		budget.categoryId = categoryId;
		budget.period = "MONTHLY";
		budget.active = true;
		budget.update(limit, thresholds);
		return budget;
	}

	public void update(Money limit, List<Integer> thresholds) {
		if (!limit.isPositive()) {
			throw ValidationException.of("limit", "must be greater than zero");
		}
		List<Integer> normalized = thresholds == null || thresholds.isEmpty() ? DEFAULT_THRESHOLDS
				: thresholds.stream().distinct().sorted().toList();
		if (normalized.size() > 5 || normalized.stream().anyMatch(t -> t < 1 || t > 200)) {
			throw ValidationException.of("alertThresholds", "use up to 5 percentages between 1 and 200");
		}
		this.limitAmount = limit.amount();
		this.currency = limit.currency();
		this.alertThresholds = normalized.toArray(Integer[]::new);
	}

	public boolean isOverall() {
		return categoryId == null;
	}

	public UUID getCategoryId() {
		return categoryId;
	}

	public Money getLimit() {
		return new Money(limitAmount, currency);
	}

	public List<Integer> getAlertThresholds() {
		return Arrays.asList(alertThresholds);
	}

	public boolean isActive() {
		return active;
	}

}
