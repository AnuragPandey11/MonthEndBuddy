package dev.monthendbuddy.identity.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.identity.UserSettingsView;

/**
 * Per-user settings, keyed by the user's id. Money columns carry no currency of their own: they are in
 * {@code baseCurrency} (architecture §6.1), so they are stored as plain decimals and exposed as {@link Money}.
 */
@Entity
@Table(name = "user_settings")
public class UserSettings {

	public static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("Asia/Kolkata");

	@Id
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "base_currency", nullable = false, length = 3)
	private CurrencyCode baseCurrency;

	@Column(name = "timezone", nullable = false)
	private String timezone;

	@Column(name = "current_savings", precision = 19, scale = 4)
	private BigDecimal currentSavings;

	@Column(name = "current_savings_updated_at")
	private Instant currentSavingsUpdatedAt;

	@Column(name = "emergency_buffer_months", nullable = false)
	private int emergencyBufferMonths;

	@Column(name = "large_txn_threshold", nullable = false, precision = 19, scale = 4)
	private BigDecimal largeTxnThreshold;

	@Column(name = "anomaly_multiplier", nullable = false, precision = 4, scale = 2)
	private BigDecimal anomalyMultiplier;

	@Column(name = "category_spike_pct", nullable = false)
	private int categorySpikePct;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected UserSettings() {
	}

	/** Defaults from architecture §7.1. */
	public static UserSettings defaultsFor(UserId user) {
		UserSettings settings = new UserSettings();
		settings.userId = user.value();
		settings.baseCurrency = CurrencyCode.INR;
		settings.timezone = DEFAULT_TIMEZONE.getId();
		settings.emergencyBufferMonths = 3;
		settings.largeTxnThreshold = new BigDecimal("25000");
		settings.anomalyMultiplier = new BigDecimal("3.0");
		settings.categorySpikePct = 40;
		return settings;
	}

	/** Back to the architecture §7.1 defaults; used by the nightly demo reset. */
	public void resetToDefaults() {
		UserSettings defaults = defaultsFor(new UserId(userId));
		this.baseCurrency = defaults.baseCurrency;
		this.timezone = defaults.timezone;
		this.currentSavings = null;
		this.currentSavingsUpdatedAt = null;
		this.emergencyBufferMonths = defaults.emergencyBufferMonths;
		this.largeTxnThreshold = defaults.largeTxnThreshold;
		this.anomalyMultiplier = defaults.anomalyMultiplier;
		this.categorySpikePct = defaults.categorySpikePct;
	}

	/** Applies the fields that are present; validation of ranges happens in {@code UserSettingsService}. */
	public void apply(ZoneId newTimezone, Integer newBufferMonths, Money newLargeTxnThreshold,
			BigDecimal newAnomalyMultiplier, Integer newCategorySpikePct) {
		if (newTimezone != null) {
			this.timezone = newTimezone.getId();
		}
		if (newBufferMonths != null) {
			this.emergencyBufferMonths = newBufferMonths;
		}
		if (newLargeTxnThreshold != null) {
			this.largeTxnThreshold = newLargeTxnThreshold.amount();
		}
		if (newAnomalyMultiplier != null) {
			this.anomalyMultiplier = newAnomalyMultiplier;
		}
		if (newCategorySpikePct != null) {
			this.categorySpikePct = newCategorySpikePct;
		}
	}

	public CurrencyCode getBaseCurrency() {
		return baseCurrency;
	}

	public void updateCurrentSavings(Money amount, Instant at) {
		this.currentSavings = amount == null ? null : amount.amount();
		this.currentSavingsUpdatedAt = at;
	}

	public UserSettingsView toView() {
		return new UserSettingsView(new UserId(userId), baseCurrency, ZoneId.of(timezone), money(currentSavings),
				currentSavingsUpdatedAt, emergencyBufferMonths, money(largeTxnThreshold), anomalyMultiplier,
				categorySpikePct);
	}

	private Money money(BigDecimal amount) {
		return amount == null ? null : new Money(amount, baseCurrency);
	}

}
