package dev.monthendbuddy.identity.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.web.MoneyDto;
import dev.monthendbuddy.common.web.PositiveMoney;
import dev.monthendbuddy.identity.CurrentUser;
import dev.monthendbuddy.identity.UserSettingsView;
import dev.monthendbuddy.identity.application.UserProfileService;
import dev.monthendbuddy.identity.application.UserSettingsService;

/** The caller's profile and settings. */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

	private final UserProfileService profiles;

	private final UserSettingsService settings;

	MeController(UserProfileService profiles, UserSettingsService settings) {
		this.profiles = profiles;
		this.settings = settings;
	}

	@GetMapping
	MeResponse me(CurrentUser user) {
		UserProfileService.Profile profile = profiles.get(user.id());
		return new MeResponse(profile.id(), profile.email(), profile.displayName(), profile.demo());
	}

	@GetMapping("/settings")
	SettingsResponse settings(CurrentUser user) {
		return response(settings.get(user.id()));
	}

	/** Partial update: omitted fields keep their value. */
	@PatchMapping("/settings")
	SettingsResponse updateSettings(CurrentUser user, @Valid @RequestBody SettingsUpdate body) {
		return response(settings.update(user.id(), new UserSettingsService.UpdateCommand(body.timezone(),
				money(body.currentSavings(), "currentSavings"), body.emergencyBufferMonths(),
				money(body.largeTxnThreshold(), "largeTxnThreshold"), body.anomalyMultiplier(),
				body.categorySpikePct())));
	}

	private static Money money(MoneyDto dto, String field) {
		return dto == null ? null : dto.toMoney(field);
	}

	private static SettingsResponse response(UserSettingsView view) {
		return new SettingsResponse(view.baseCurrency(), view.timezone().getId(),
				view.currentSavings() == null ? null : MoneyDto.from(view.currentSavings()),
				view.currentSavingsUpdatedAt(), view.emergencyBufferMonths(), MoneyDto.from(view.largeTxnThreshold()),
				view.anomalyMultiplier(), view.categorySpikePct());
	}

	record MeResponse(UUID id, String email, String displayName, boolean demo) {
	}

	record SettingsUpdate(@Size(max = 64) String timezone, @PositiveMoney(allowZero = true) MoneyDto currentSavings,
			@Min(0) @Max(24) Integer emergencyBufferMonths, @PositiveMoney MoneyDto largeTxnThreshold,
			@DecimalMin("1.5") @DecimalMax("10") @Digits(integer = 2, fraction = 2) BigDecimal anomalyMultiplier,
			@Min(10) @Max(500) Integer categorySpikePct) {
	}

	record SettingsResponse(CurrencyCode baseCurrency, String timezone, MoneyDto currentSavings,
			Instant currentSavingsUpdatedAt, int emergencyBufferMonths, MoneyDto largeTxnThreshold,
			BigDecimal anomalyMultiplier, int categorySpikePct) {
	}

}
