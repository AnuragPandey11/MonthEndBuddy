package dev.monthendbuddy.transactions.domain;

import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dev.monthendbuddy.common.TextSanitizer;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.common.persistence.UserOwnedEntity;
import dev.monthendbuddy.transactions.MatchType;

/**
 * "When the description or merchant matches {@code pattern}, use {@code categoryId}." Lower {@code priority} wins;
 * ties go to the older rule. Patterns are stored normalised so matching is case- and spacing-insensitive.
 */
@Entity
@Table(name = "category_rules")
public class CategoryRule extends UserOwnedEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "match_type", nullable = false)
	private MatchType matchType;

	@Column(name = "pattern", nullable = false)
	private String pattern;

	@Column(name = "category_id", nullable = false)
	private UUID categoryId;

	@Column(name = "priority", nullable = false)
	private int priority;

	protected CategoryRule() {
	}

	public static CategoryRule create(UserId owner, MatchType matchType, String pattern, UUID categoryId,
			int priority) {
		CategoryRule rule = new CategoryRule();
		rule.userId = owner;
		rule.update(matchType, pattern, categoryId, priority);
		return rule;
	}

	public void update(MatchType matchType, String pattern, UUID categoryId, int priority) {
		String normalized = normalize(pattern);
		if (normalized == null) {
			throw ValidationException.of("pattern", "must not be blank");
		}
		this.matchType = matchType;
		this.pattern = normalized;
		this.categoryId = categoryId;
		this.priority = priority;
	}

	/** Whether this rule matches either field; both are normalised first. */
	public boolean matches(String description, String merchant) {
		return matchesField(normalize(description)) || matchesField(normalize(merchant));
	}

	private boolean matchesField(String text) {
		if (text == null) {
			return false;
		}
		return switch (matchType) {
			case CONTAINS -> text.contains(pattern);
			case STARTS_WITH -> text.startsWith(pattern);
			case EQUALS -> text.equals(pattern);
		};
	}

	/** Lower-case, sanitised, single spaces. */
	public static String normalize(String text) {
		String clean = TextSanitizer.singleLine(text);
		return clean == null ? null : clean.toLowerCase(Locale.ROOT);
	}

	public MatchType getMatchType() {
		return matchType;
	}

	public String getPattern() {
		return pattern;
	}

	public UUID getCategoryId() {
		return categoryId;
	}

	public int getPriority() {
		return priority;
	}

}
