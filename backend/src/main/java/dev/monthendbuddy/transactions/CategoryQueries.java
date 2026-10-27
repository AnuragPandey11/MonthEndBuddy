package dev.monthendbuddy.transactions;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.monthendbuddy.common.UserId;

/** Category lookups for other modules (architecture §4.1). Another user's category is never found. */
public interface CategoryQueries {

	/** A category of the given kind that the user can use now (visible and not archived). */
	Optional<CategoryRef> findUsable(UserId user, UUID categoryId, CategoryKind kind);

	/** Categories the user can use now (visible and not archived), for matching a category name to an ID. */
	List<CategoryRef> usable(UserId user);

	/** A system category by name, case-insensitively (seed data refers to categories by name). */
	Optional<CategoryRef> findSystem(String name);

	/** Names of categories visible to the user, archived ones included. */
	Map<UUID, String> names(UserId user, Collection<UUID> categoryIds);

}
