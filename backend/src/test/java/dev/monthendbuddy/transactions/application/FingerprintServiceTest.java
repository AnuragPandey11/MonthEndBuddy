package dev.monthendbuddy.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.transactions.TransactionType;

class FingerprintServiceTest {

	private final FingerprintService service = new FingerprintService();

	private final UserId user = new UserId(UUID.randomUUID());

	private final LocalDate date = LocalDate.of(2026, 9, 3);

	private String fp(UserId u, String amount, String description) {
		return service.fingerprint(u, date, TransactionType.EXPENSE, new BigDecimal(amount), description);
	}

	@Test
	void normalisesDescriptionAndAmountScale() {
		assertThat(fp(user, "1250.5", "Cafe  Coffee-Day!")).isEqualTo(fp(user, "1250.50", "cafe coffee day"))
			.hasSize(64)
			.matches("[0-9a-f]+");
		assertThat(FingerprintService.normalize("  UPI/ZOMATO   Order #42 ")).isEqualTo("upizomatoorder42");
	}

	@Test
	void anyMeaningfulDifferenceChangesTheFingerprint() {
		String base = fp(user, "100", "Coffee");
		assertThat(fp(user, "100.01", "Coffee")).isNotEqualTo(base);
		assertThat(fp(user, "100", "Tea")).isNotEqualTo(base);
		assertThat(fp(new UserId(UUID.randomUUID()), "100", "Coffee")).isNotEqualTo(base);
		assertThat(service.fingerprint(user, date, TransactionType.INCOME, new BigDecimal("100"), "Coffee"))
			.isNotEqualTo(base);
	}

}
