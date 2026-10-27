package dev.monthendbuddy.common.cache;

import java.util.function.Supplier;

import dev.monthendbuddy.common.UserId;

/**
 * Cache-aside for per-user aggregates (architecture §11.1). Implementations must read the generation before calling
 * the loader, and must fall back to the loader when the cache is unavailable. Only aggregates are cached, never rows.
 */
public interface UserScopedCache {

	<T> T getOrLoad(UserId user, String kind, String params, Class<T> type, Supplier<T> loader);

}
