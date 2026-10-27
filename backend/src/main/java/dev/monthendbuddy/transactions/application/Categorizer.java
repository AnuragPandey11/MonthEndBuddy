package dev.monthendbuddy.transactions.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.transactions.TransactionType;
import dev.monthendbuddy.transactions.domain.Category;
import dev.monthendbuddy.transactions.domain.CategoryRule;
import dev.monthendbuddy.transactions.infrastructure.CategoryRepository;
import dev.monthendbuddy.transactions.infrastructure.CategoryRuleRepository;

/**
 * Applies a user's category rules in priority order (architecture §8.1). A rule is skipped when its category no
 * longer fits: archived, or of the other kind than the transaction. Imports load the rules once with
 * {@link #forUser} instead of once per row.
 */
@Component
public class Categorizer {

	private final CategoryRuleRepository rules;

	private final CategoryRepository categories;

	public Categorizer(CategoryRuleRepository rules, CategoryRepository categories) {
		this.rules = rules;
		this.categories = categories;
	}

	@Transactional(readOnly = true)
	public Optional<UUID> categorize(UserId user, TransactionType type, String description, String merchant) {
		return forUser(user).categorize(type, description, merchant);
	}

	/** A snapshot of the user's rules and their categories, for categorising many rows. */
	@Transactional(readOnly = true)
	public Snapshot forUser(UserId user) {
		List<CategoryRule> ordered = rules.findInPriorityOrder(user);
		Map<UUID, Category> usable = categories.findUsable(user)
			.stream()
			.collect(Collectors.toMap(Category::getId, Function.identity()));
		return new Snapshot(ordered, usable);
	}

	public record Snapshot(List<CategoryRule> rules, Map<UUID, Category> usableCategories) {

		public Optional<UUID> categorize(TransactionType type, String description, String merchant) {
			for (CategoryRule rule : rules) {
				Category category = usableCategories.get(rule.getCategoryId());
				if (category != null && category.appliesTo(type) && rule.matches(description, merchant)) {
					return Optional.of(category.getId());
				}
			}
			return Optional.empty();
		}

	}

}
