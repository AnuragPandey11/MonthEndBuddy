package dev.monthendbuddy.identity.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.cache.CacheGenerationService;
import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.common.error.UnsupportedCurrencyException;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.identity.UserSavingsCommands;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.identity.UserSettingsView;
import dev.monthendbuddy.identity.domain.UserSettings;
import dev.monthendbuddy.identity.infrastructure.UserSettingsRepository;

/** User settings (architecture §7.1). Updates bump the cache generation: timezone and thresholds change figures. */
@Service
public class UserSettingsService implements UserSettingsQuery, UserSavingsCommands {

	private final UserSettingsRepository settings;

	private final CacheGenerationService cacheGeneration;

	private final Clock clock;

	public UserSettingsService(UserSettingsRepository settings, CacheGenerationService cacheGeneration, Clock clock) {
		this.settings = settings;
		this.cacheGeneration = cacheGeneration;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public UserSettingsView get(UserId userId) {
		return require(userId).toView();
	}

	@Transactional
	public UserSettingsView createDefaults(UserId userId) {
		return settings.save(UserSettings.defaultsFor(userId)).toView();
	}

	/** Updates the fields that are present; ranges follow LLD §11.2. */
	@Transactional
	public UserSettingsView update(UserId userId, UpdateCommand command) {
		UserSettings current = require(userId);
		ZoneId zone = null;
		if (command.timezone() != null) {
			try {
				zone = ZoneId.of(command.timezone());
			}
			catch (DateTimeException e) {
				throw ValidationException.of("timezone", "is not a known time zone, e.g. Asia/Kolkata");
			}
		}
		if (command.currentSavings() != null) {
			requireBase(current, command.currentSavings(), "currentSavings");
			if (command.currentSavings().isNegative()) {
				throw ValidationException.of("currentSavings", "must not be negative");
			}
			current.updateCurrentSavings(command.currentSavings(), clock.instant());
		}
		if (command.largeTxnThreshold() != null) {
			requireBase(current, command.largeTxnThreshold(), "largeTxnThreshold");
		}
		current.apply(zone, command.emergencyBufferMonths(), command.largeTxnThreshold(), command.anomalyMultiplier(),
				command.categorySpikePct());
		settings.save(current);
		cacheGeneration.bumpAfterCommit(userId);
		return current.toView();
	}

	@Override
	@Transactional
	public UserSettingsView addToCurrentSavings(UserId userId, Money amount) {
		UserSettings current = require(userId);
		requireBase(current, amount, "amount");
		Money before = current.toView().currentSavings();
		if (before == null) {
			throw new IllegalStateException("Savings today is unknown, so it cannot be added to");
		}
		current.updateCurrentSavings(before.add(amount), clock.instant());
		settings.save(current);
		cacheGeneration.bumpAfterCommit(userId);
		return current.toView();
	}

	private UserSettings require(UserId userId) {
		return settings.findById(userId.value()).orElseThrow(() -> new NotFoundException("Settings"));
	}

	private static void requireBase(UserSettings settings, Money money, String field) {
		if (money.currency() != settings.getBaseCurrency()) {
			throw new UnsupportedCurrencyException(field + ".currency", settings.getBaseCurrency());
		}
	}

	public record UpdateCommand(String timezone, Money currentSavings, Integer emergencyBufferMonths,
			Money largeTxnThreshold, BigDecimal anomalyMultiplier, Integer categorySpikePct) {
	}

}
