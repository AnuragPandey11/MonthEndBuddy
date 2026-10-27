package dev.monthendbuddy.transactions.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.TextSanitizer;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.cache.CacheGenerationService;
import dev.monthendbuddy.common.error.ConflictException;
import dev.monthendbuddy.common.error.ForbiddenException;
import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.transactions.CategoryKind;
import dev.monthendbuddy.transactions.CategoryQueries;
import dev.monthendbuddy.transactions.CategoryRef;
import dev.monthendbuddy.transactions.TransactionType;
import dev.monthendbuddy.transactions.domain.Category;
import dev.monthendbuddy.transactions.infrastructure.CategoryRepository;

/**
 * System and custom categories (architecture §8.1). Another user's category behaves exactly like a missing one;
 * system categories are visible but read-only (403).
 */
@Service
public class CategoryService implements CategoryQueries {

	private final CategoryRepository categories;

	private final CacheGenerationService cacheGeneration;

	public CategoryService(CategoryRepository categories, CacheGenerationService cacheGeneration) {
		this.categories = categories;
		this.cacheGeneration = cacheGeneration;
	}

	@Transactional(readOnly = true)
	public List<CategoryView> listUsable(UserId user) {
		return categories.findUsable(user).stream().map(CategoryView::of).toList();
	}

	@Transactional
	public CategoryView create(UserId user, String name, CategoryKind kind, String icon) {
		Category category = Category.custom(user, name, kind, icon);
		requireNameFree(user, category.getName(), new UUID(0, 0));
		return CategoryView.of(categories.saveAndFlush(category));
	}

	@Transactional
	public CategoryView update(UserId user, UUID id, String name, String icon) {
		Category category = requireEditable(user, id);
		if (name != null) {
			String cleaned = TextSanitizer.singleLine(name);
			if (cleaned != null && !cleaned.equalsIgnoreCase(category.getName())) {
				requireNameFree(user, cleaned, id);
			}
			category.rename(name);
		}
		if (icon != null) {
			category.changeIcon(icon);
		}
		// Category names appear in cached breakdowns.
		cacheGeneration.bumpAfterCommit(user);
		return CategoryView.of(categories.saveAndFlush(category));
	}

	@Transactional
	public void archive(UserId user, UUID id) {
		requireEditable(user, id).archive();
		cacheGeneration.bumpAfterCommit(user);
	}

	/**
	 * The category a transaction may use: visible to the user, not archived, and of the transaction's kind.
	 * @throws ValidationException 422 on {@code categoryId} otherwise (architecture §8.1)
	 */
	@Transactional(readOnly = true)
	public Category requireUsable(UserId user, UUID categoryId, TransactionType type) {
		Category category = categories.findVisible(categoryId, user)
			.filter(c -> !c.isArchived())
			.orElseThrow(() -> ValidationException.of("categoryId", "is not a category you can use"));
		if (!category.appliesTo(type)) {
			throw ValidationException.of("categoryId", "is an " + category.getKind().name().toLowerCase()
					+ " category and cannot be used for this transaction type");
		}
		return category;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<CategoryRef> findUsable(UserId user, UUID categoryId, CategoryKind kind) {
		return categories.findVisible(categoryId, user)
			.filter(c -> !c.isArchived() && c.getKind() == kind)
			.map(c -> new CategoryRef(c.getId(), c.getName()));
	}

	@Override
	@Transactional(readOnly = true)
	public List<CategoryRef> usable(UserId user) {
		return categories.findUsable(user).stream().map(c -> new CategoryRef(c.getId(), c.getName())).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<CategoryRef> findSystem(String name) {
		return categories.findSystem()
			.stream()
			.filter(c -> c.getName().equalsIgnoreCase(name))
			.findFirst()
			.map(c -> new CategoryRef(c.getId(), c.getName()));
	}

	@Override
	@Transactional(readOnly = true)
	public Map<UUID, String> names(UserId user, Collection<UUID> categoryIds) {
		Map<UUID, String> names = new HashMap<>();
		for (UUID id : categoryIds) {
			if (id != null) {
				categories.findVisible(id, user).ifPresent(c -> names.put(c.getId(), c.getName()));
			}
		}
		return names;
	}

	private Category requireEditable(UserId user, UUID id) {
		Category category = categories.findVisible(id, user).orElseThrow(() -> new NotFoundException("Category"));
		if (category.isSystem()) {
			throw new ForbiddenException("category_read_only", "System categories cannot be changed");
		}
		return category;
	}

	private void requireNameFree(UserId user, String name, UUID excludeId) {
		if (categories.isNameTaken(name, user, excludeId)) {
			throw new ConflictException("category_name_taken", "A category with this name already exists");
		}
	}

	public record CategoryView(UUID id, String name, CategoryKind kind, String icon, boolean system) {

		static CategoryView of(Category c) {
			return new CategoryView(c.getId(), c.getName(), c.getKind(), c.getIcon(), c.isSystem());
		}

	}

}
