package dev.monthendbuddy.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import dev.monthendbuddy.identity.domain.UserSettings;

public interface UserSettingsRepository extends Repository<UserSettings, UUID> {

	Optional<UserSettings> findById(UUID userId);

	UserSettings save(UserSettings settings);

}
