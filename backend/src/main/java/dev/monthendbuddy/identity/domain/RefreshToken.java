package dev.monthendbuddy.identity.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.IllegalStateTransitionException;

/**
 * One opaque refresh token, stored only as a SHA-256 hash. Tokens in one family form a rotation chain: each use
 * revokes the token and issues its successor, and presenting a revoked token again means it was stolen, so the whole
 * family is revoked (architecture §5.2, LLD §3).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UserId userId;

	@Column(name = "token_hash", nullable = false, updatable = false)
	private String tokenHash;

	@Column(name = "family_id", nullable = false, updatable = false)
	private UUID familyId;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "replaced_by")
	private UUID replacedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RefreshToken() {
	}

	private RefreshToken(UserId userId, String tokenHash, UUID familyId, Instant now, Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.familyId = familyId;
		this.createdAt = now;
		this.expiresAt = expiresAt;
	}

	/** The first token of a new login session. */
	public static RefreshToken startFamily(UserId userId, String tokenHash, Instant now, Instant expiresAt) {
		return new RefreshToken(userId, tokenHash, UUID.randomUUID(), now, expiresAt);
	}

	/** The successor of this token, in the same family. */
	public RefreshToken successor(String tokenHash, Instant now, Instant expiresAt) {
		return new RefreshToken(userId, tokenHash, familyId, now, expiresAt);
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

	public boolean isActive(Instant now) {
		return !isRevoked() && !isExpired(now);
	}

	/** Marks this token as used and replaced; only an active token may rotate. */
	public void rotate(RefreshToken next, Instant now) {
		if (!isActive(now)) {
			throw new IllegalStateTransitionException("Only an active refresh token can be rotated");
		}
		this.revokedAt = now;
		this.replacedBy = next.id;
	}

	public void revoke(Instant now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

	public UUID getId() {
		return id;
	}

	public UserId getUserId() {
		return userId;
	}

	public UUID getFamilyId() {
		return familyId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public UUID getReplacedBy() {
		return replacedBy;
	}

}
