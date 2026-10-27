package dev.monthendbuddy.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dev.monthendbuddy.common.TextSanitizer;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.IllegalStateTransitionException;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.common.persistence.BaseEntity;
import dev.monthendbuddy.transactions.CategoryKind;
import dev.monthendbuddy.transactions.TransactionType;

/**
 * A category: system-defined ({@code userId == null}, read-only, visible to all) or a user's own. Custom categories
 * are archived, never deleted, because transactions keep referring to them.
 */
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

	@Column(name = "user_id", updatable = false)
	private UserId userId;

	@Column(name = "name", nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, updatable = false)
	private CategoryKind kind;

	@Column(name = "icon")
	private String icon;

	@Column(name = "archived", nullable = false)
	private boolean archived;

	protected Category() {
	}

	public static Category custom(UserId owner, String name, CategoryKind kind, String icon) {
		Category category = new Category();
		category.userId = owner;
		category.name = requireName(name);
		category.kind = kind;
		category.icon = TextSanitizer.singleLine(icon);
		return category;
	}

	public boolean isSystem() {
		return userId == null;
	}

	public boolean isVisibleTo(UserId user) {
		return isSystem() || userId.equals(user);
	}

	public boolean appliesTo(TransactionType type) {
		return kind.name().equals(type.name());
	}

	public void rename(String newName) {
		requireCustom();
		this.name = requireName(newName);
	}

	public void changeIcon(String newIcon) {
		requireCustom();
		this.icon = TextSanitizer.singleLine(newIcon);
	}

	public void archive() {
		requireCustom();
		this.archived = true;
	}

	private void requireCustom() {
		if (isSystem()) {
			throw new IllegalStateTransitionException("System categories are read-only");
		}
	}

	private static String requireName(String name) {
		String cleaned = TextSanitizer.singleLine(name);
		if (cleaned == null) {
			throw ValidationException.of("name", "must not be blank");
		}
		return cleaned;
	}

	public UserId getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public CategoryKind getKind() {
		return kind;
	}

	public String getIcon() {
		return icon;
	}

	public boolean isArchived() {
		return archived;
	}

}
