package dev.monthendbuddy.goals.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.TextSanitizer;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.common.persistence.UserOwnedEntity;
import dev.monthendbuddy.goals.GoalStatus;

/** A savings target with a deadline. It becomes ACHIEVED as soon as the saved amount reaches the target. */
@Entity
@Table(name = "savings_goals")
public class SavingsGoal extends UserOwnedEntity {

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal targetAmount;

	@Column(name = "current_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal currentAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "currency", nullable = false, length = 3)
	private CurrencyCode currency;

	@Column(name = "target_date", nullable = false)
	private LocalDate targetDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private GoalStatus status;

	@Column(name = "priority", nullable = false)
	private int priority;

	protected SavingsGoal() {
	}

	public static SavingsGoal create(UserId owner, String name, Money target, Money current, LocalDate targetDate,
			int priority) {
		SavingsGoal goal = new SavingsGoal();
		goal.userId = owner;
		goal.status = GoalStatus.ACTIVE;
		goal.update(name, target, current, targetDate, priority);
		return goal;
	}

	public void update(String newName, Money target, Money current, LocalDate newTargetDate, int newPriority) {
		String cleaned = TextSanitizer.singleLine(newName);
		if (cleaned == null) {
			throw ValidationException.of("name", "must not be blank");
		}
		if (!target.isPositive()) {
			throw ValidationException.of("targetAmount", "must be greater than zero");
		}
		if (current.isNegative()) {
			throw ValidationException.of("currentAmount", "must not be negative");
		}
		this.name = cleaned;
		this.targetAmount = target.amount();
		this.currentAmount = current.amount();
		this.currency = target.currency();
		this.targetDate = newTargetDate;
		this.priority = newPriority;
		refreshStatus();
	}

	/** Adds to the saved amount (the month-end review); there is still no contributions ledger (architecture §9.3). */
	public void contribute(Money amount) {
		if (!amount.isPositive()) {
			throw ValidationException.of("amount", "must be greater than zero");
		}
		this.currentAmount = getCurrent().add(amount).amount();
		refreshStatus();
	}

	public void archive() {
		this.status = GoalStatus.ARCHIVED;
	}

	private void refreshStatus() {
		if (status != GoalStatus.ARCHIVED) {
			status = getCurrent().isGreaterThanOrEqual(getTarget()) ? GoalStatus.ACHIEVED : GoalStatus.ACTIVE;
		}
	}

	public Money remaining() {
		Money remaining = getTarget().subtract(getCurrent());
		return remaining.isNegative() ? Money.zero(currency) : remaining;
	}

	public String getName() {
		return name;
	}

	public Money getTarget() {
		return new Money(targetAmount, currency);
	}

	public Money getCurrent() {
		return new Money(currentAmount, currency);
	}

	public LocalDate getTargetDate() {
		return targetDate;
	}

	public GoalStatus getStatus() {
		return status;
	}

	public int getPriority() {
		return priority;
	}

}
