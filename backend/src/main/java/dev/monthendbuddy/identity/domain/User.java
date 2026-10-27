package dev.monthendbuddy.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.persistence.BaseEntity;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

	/** Stored lower-cased; the column is {@code citext}, so uniqueness is case-insensitive as well. */
	@Column(name = "email", nullable = false, columnDefinition = "citext")
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "is_demo", nullable = false)
	private boolean demo;

	protected User() {
	}

	private User(String email, String passwordHash, String displayName, boolean demo) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.demo = demo;
	}

	public static User register(String normalizedEmail, String passwordHash, String displayName) {
		return new User(normalizedEmail, passwordHash, displayName, false);
	}

	public static User demo(String normalizedEmail, String passwordHash, String displayName) {
		return new User(normalizedEmail, passwordHash, displayName, true);
	}

	public void changePassword(String newHash) {
		this.passwordHash = newHash;
	}

	public UserId userId() {
		return new UserId(id);
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public boolean isDemo() {
		return demo;
	}

}
