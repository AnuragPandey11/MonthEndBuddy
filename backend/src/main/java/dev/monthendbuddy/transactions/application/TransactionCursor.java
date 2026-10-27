package dev.monthendbuddy.transactions.application;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.transactions.TransactionView;
import dev.monthendbuddy.transactions.infrastructure.TransactionQueryRepository.Keyset;

/**
 * Opaque keyset cursor: base64url of {@code date|id}, the sort position of the last row on a page. It reveals nothing
 * that the page itself did not, so it needs no signing; a malformed cursor is a 422.
 */
final class TransactionCursor {

	private TransactionCursor() {
	}

	static String encode(TransactionView last) {
		String raw = last.transactionDate() + "|" + last.id();
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	static Keyset decode(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			int bar = raw.indexOf('|');
			return new Keyset(LocalDate.parse(raw.substring(0, bar)), UUID.fromString(raw.substring(bar + 1)));
		}
		catch (RuntimeException e) {
			throw ValidationException.of("cursor", "is invalid");
		}
	}

}
