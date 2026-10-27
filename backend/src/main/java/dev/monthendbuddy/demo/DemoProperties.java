package dev.monthendbuddy.demo;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code meb.demo}: the shared demo account (architecture §5.2).
 *
 * @param enabled seed the demo account at startup and reset it nightly
 * @param seed generator seed
 * @param resetCron when the nightly reset runs, in Asia/Kolkata time
 * @param currentSavings the demo account's stated savings, so purchase simulations have an input. It is a total that
 *        includes what the demo goals hold (₹1,85,000 + ₹22,000), leaving ₹1,03,000 free.
 */
@ConfigurationProperties("meb.demo")
public record DemoProperties(Boolean enabled, Long seed, String resetCron, BigDecimal currentSavings) {

	public DemoProperties {
		enabled = enabled == null || enabled;
		seed = seed == null ? SampleDataExporter.SEED : seed;
		resetCron = resetCron == null ? "0 0 3 * * *" : resetCron;
		currentSavings = currentSavings == null ? new BigDecimal("310000") : currentSavings;
	}

}
