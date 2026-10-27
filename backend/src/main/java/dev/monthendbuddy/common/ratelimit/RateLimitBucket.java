package dev.monthendbuddy.common.ratelimit;

/**
 * Rate-limit buckets (architecture §11.2). Limits come from {@code meb.ratelimit.limits.<bucket>}. Most buckets count
 * requests; {@code PROVIDER_TOKENS} counts model tokens, because the provider's free tier limits tokens per minute.
 */
public enum RateLimitBucket {

	LOGIN, REGISTER, DEMO_LOGIN, AGENT_CHAT, PROVIDER_GLOBAL, PROVIDER_TOKENS, CSV_IMPORT

}
