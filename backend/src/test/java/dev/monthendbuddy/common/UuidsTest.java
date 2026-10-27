package dev.monthendbuddy.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidsTest {

	@Test
	void producesVersion7WithTheRfcVariant() {
		UUID uuid = Uuids.v7();
		assertThat(uuid.version()).isEqualTo(7);
		assertThat(uuid.variant()).isEqualTo(2);
	}

	@Test
	void embedsTheCurrentTimeInTheFirst48Bits() {
		long before = System.currentTimeMillis();
		long embedded = Uuids.v7().getMostSignificantBits() >>> 16;
		long after = System.currentTimeMillis();
		assertThat(embedded).isBetween(before, after);
	}

	@Test
	void isUniqueAcrossManyCalls() {
		Set<UUID> seen = new HashSet<>();
		for (int i = 0; i < 10_000; i++) {
			assertThat(seen.add(Uuids.v7())).isTrue();
		}
	}

}
