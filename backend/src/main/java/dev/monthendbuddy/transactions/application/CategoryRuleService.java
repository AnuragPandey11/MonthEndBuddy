package dev.monthendbuddy.transactions.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.ConflictException;
import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.transactions.CategoryRef;
import dev.monthendbuddy.transactions.MatchType;
import dev.monthendbuddy.transactions.domain.Category;
import dev.monthendbuddy.transactions.domain.CategoryRule;
import dev.monthendbuddy.transactions.infrastructure.CategoryRepository;
import dev.monthendbuddy.transactions.infrastructure.CategoryRuleRepository;

/** CRUD for category rules; a rule may only point at a category the user can use. */
@Service
public class CategoryRuleService {

	private final CategoryRuleRepository rules;

	private final CategoryRepository categories;

	public CategoryRuleService(CategoryRuleRepository rules, CategoryRepository categories) {
		this.rules = rules;
		this.categories = categories;
	}

	@Transactional(readOnly = true)
	public List<RuleView> list(UserId user) {
		Map<UUID, Category> visible = categories.findUsable(user)
			.stream()
			.collect(Collectors.toMap(Category::getId, Function.identity()));
		return rules.findInPriorityOrder(user).stream().map(r -> view(r, visible.get(r.getCategoryId()))).toList();
	}

	@Transactional
	public RuleView create(UserId user, MatchType matchType, String pattern, UUID categoryId, int priority) {
		Category category = requireCategory(user, categoryId);
		return view(rules.saveAndFlush(CategoryRule.create(user, matchType, pattern, categoryId, priority)), category);
	}

	@Transactional
	public RuleView update(UserId user, UUID id, MatchType matchType, String pattern, UUID categoryId, int priority,
			long expectedVersion) {
		CategoryRule rule = rules.findByIdAndUserId(id, user).orElseThrow(() -> new NotFoundException("Rule"));
		if (rule.getVersion() != expectedVersion) {
			throw ConflictException.versionConflict();
		}
		Category category = requireCategory(user, categoryId);
		rule.update(matchType, pattern, categoryId, priority);
		return view(rules.saveAndFlush(rule), category);
	}

	@Transactional
	public void delete(UserId user, UUID id) {
		if (rules.deleteByIdAndUserId(id, user) == 0) {
			throw new NotFoundException("Rule");
		}
	}

	private Category requireCategory(UserId user, UUID categoryId) {
		return categories.findVisible(categoryId, user)
			.filter(c -> !c.isArchived())
			.orElseThrow(() -> ValidationException.of("categoryId", "is not a category you can use"));
	}

	private static RuleView view(CategoryRule rule, Category category) {
		return new RuleView(rule.getId(), rule.getMatchType(), rule.getPattern(),
				new CategoryRef(rule.getCategoryId(), category == null ? null : category.getName()), rule.getPriority(),
				rule.getVersion());
	}

	/** {@code category.name} is {@code null} when the rule's category has since been archived (the rule is inert). */
	public record RuleView(UUID id, MatchType matchType, String pattern, CategoryRef category, int priority,
			long version) {
	}

}
