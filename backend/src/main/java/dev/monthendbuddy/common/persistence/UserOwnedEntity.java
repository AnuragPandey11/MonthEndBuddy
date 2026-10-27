package dev.monthendbuddy.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import dev.monthendbuddy.common.UserId;

/** An entity that belongs to exactly one user; its owner never changes (architecture §5.3). */
@MappedSuperclass
public abstract class UserOwnedEntity extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	protected UserId userId;

	protected UserOwnedEntity() {
	}

	protected UserOwnedEntity(UserId userId) {
		this.userId = userId;
	}

	public UserId getUserId() {
		return userId;
	}

	public boolean isOwnedBy(UserId user) {
		return userId.equals(user);
	}

}
