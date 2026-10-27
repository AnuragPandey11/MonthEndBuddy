package dev.monthendbuddy.transactions.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.stereotype.Component;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.transactions.TransactionType;

/**
 * The documented, deterministic duplicate strategy (architecture §8.3):
 *
 * <pre>
 * key         = user_id | date (ISO) | type | amount (scale 2) | normalise(description)
 * normalise   = lower-case, collapse whitespace, strip non-alphanumerics
 * fingerprint = SHA-256(key), hex
 * </pre>
 *
 * Rows sharing a fingerprint within one file get sequence numbers 1, 2, 3… in file order, so two genuine identical
 * coffees on the same day are both imported, and the same file uploaded again matches row for row.
 */
@Component
public class FingerprintService {

	public String fingerprint(UserId user, LocalDate date, TransactionType type, BigDecimal amount, String description) {
		String key = user + "|" + date + "|" + type + "|" + amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString()
				+ "|" + normalize(description);
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	static String normalize(String description) {
		return description == null ? ""
				: description.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replaceAll("[^\\p{L}\\p{N}]", "");
	}

}
