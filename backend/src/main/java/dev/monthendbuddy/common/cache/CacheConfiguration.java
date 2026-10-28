package dev.monthendbuddy.common.cache;

import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.monthendbuddy.common.UserId;

/**
 * Null-object cache while {@code meb.cache.enabled=false} (the core stack): every read hits the database.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CacheProperties.class)
@ConditionalOnProperty(name = "meb.cache.enabled", havingValue = "false", matchIfMissing = true)
class CacheConfiguration {

	@Bean
	UserScopedCache noOpUserScopedCache() {
		return new UserScopedCache() {
			@Override
			public <T> T getOrLoad(UserId user, String kind, String params, Class<T> type, Supplier<T> loader) {
				return loader.get();
			}
		};
	}

	@Bean
	CacheGenerationService noOpCacheGenerationService() {
		return new CacheGenerationService() {
			@Override
			public long current(UserId user) {
				return 0;
			}

			@Override
			public void bump(UserId user) {
			}
		};
	}

}
