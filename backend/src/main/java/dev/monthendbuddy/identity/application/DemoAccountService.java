package dev.monthendbuddy.identity.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.identity.DemoAccounts;
import dev.monthendbuddy.identity.domain.User;
import dev.monthendbuddy.identity.domain.UserSettings;
import dev.monthendbuddy.identity.infrastructure.UserRepository;
import dev.monthendbuddy.identity.infrastructure.UserSettingsRepository;

@Service
class DemoAccountService implements DemoAccounts {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository users;

	private final UserSettingsRepository settings;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	DemoAccountService(UserRepository users, UserSettingsRepository settings, PasswordEncoder passwordEncoder,
			Clock clock) {
		this.users = users;
		this.settings = settings;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean demoAccountExists() {
		return users.findByEmail(DEMO_EMAIL).filter(User::isDemo).isPresent();
	}

	@Override
	@Transactional
	public UserId resetDemoAccount(Money currentSavings) {
		User user = users.findByEmail(DEMO_EMAIL).orElseGet(this::createDemoUser);
		if (!user.isDemo()) {
			throw new IllegalStateException(DEMO_EMAIL + " belongs to a non-demo account; refusing to reset it");
		}
		UserSettings userSettings = settings.findById(user.getId())
			.orElseGet(() -> UserSettings.defaultsFor(user.userId()));
		userSettings.resetToDefaults();
		userSettings.updateCurrentSavings(currentSavings, clock.instant());
		settings.save(userSettings);
		return user.userId();
	}

	/** The password is random and never shown, so the account is reachable only through {@code POST /auth/demo}. */
	private User createDemoUser() {
		byte[] secret = new byte[32];
		RANDOM.nextBytes(secret);
		String password = Base64.getEncoder().encodeToString(secret);
		return users.saveAndFlush(User.demo(DEMO_EMAIL, passwordEncoder.encode(password), "Demo User"));
	}

}
