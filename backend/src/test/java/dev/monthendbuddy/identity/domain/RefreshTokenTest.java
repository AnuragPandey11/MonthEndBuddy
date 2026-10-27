package dev.monthendbuddy.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.IllegalStateTransitionException;

class RefreshTokenTest {

	private final Instant now = Instant.parse("2026-10-26T10:00:00Z");

	private final UserId user = new UserId(UUID.randomUUID());

	private RefreshToken fresh() {
		return RefreshToken.startFamily(user, "hash-1", now, now.plus(Duration.ofDays(14)));
	}

	@Test
	void newTokenIsActiveUntilItExpires() {
		RefreshToken token = fresh();
		assertThat(token.isActive(now)).isTrue();
		assertThat(token.isActive(now.plus(Duration.ofDays(14)).minusSeconds(1))).isTrue();
		assertThat(token.isActive(now.plus(Duration.ofDays(14)))).isFalse();
	}

	@Test
	void rotationRevokesTheTokenAndLinksASuccessorInTheSameFamily() {
		RefreshToken token = fresh();
		RefreshToken next = token.successor("hash-2", now, now.plus(Duration.ofDays(14)));
		token.rotate(next, now);
		assertThat(token.isRevoked()).isTrue();
		assertThat(token.getReplacedBy()).isEqualTo(next.getId());
		assertThat(next.getFamilyId()).isEqualTo(token.getFamilyId());
		assertThat(next.getUserId()).isEqualTo(user);
		assertThat(next.isActive(now)).isTrue();
	}

	@Test
	void aRevokedOrExpiredTokenCannotRotate() {
		RefreshToken revoked = fresh();
		revoked.revoke(now);
		assertThatThrownBy(() -> revoked.rotate(revoked.successor("h", now, now.plusSeconds(60)), now))
			.isInstanceOf(IllegalStateTransitionException.class);

		RefreshToken expired = fresh();
		Instant later = now.plus(Duration.ofDays(15));
		assertThatThrownBy(() -> expired.rotate(expired.successor("h", later, later.plusSeconds(60)), later))
			.isInstanceOf(IllegalStateTransitionException.class);
	}

	@Test
	void eachFamilyIsNew() {
		assertThat(fresh().getFamilyId()).isNotEqualTo(fresh().getFamilyId());
	}

}
