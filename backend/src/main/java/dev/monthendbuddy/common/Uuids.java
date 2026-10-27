package dev.monthendbuddy.common;

import java.security.SecureRandom;
import java.util.UUID;

/** Time-ordered UUIDv7 (RFC 9562), used for event IDs (architecture §12.3). */
public final class Uuids {

	private static final SecureRandom RANDOM = new SecureRandom();

	private Uuids() {
	}

	public static UUID v7() {
		long millis = System.currentTimeMillis();
		long msb = ((millis & 0xFFFF_FFFF_FFFFL) << 16) | 0x7000L | (RANDOM.nextInt() & 0x0FFFL);
		long lsb = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
		return new UUID(msb, lsb);
	}

}
