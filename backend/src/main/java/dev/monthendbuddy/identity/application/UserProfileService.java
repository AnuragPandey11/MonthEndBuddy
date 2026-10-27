package dev.monthendbuddy.identity.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.identity.infrastructure.UserRepository;

@Service
public class UserProfileService {

	private final UserRepository users;

	public UserProfileService(UserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public Profile get(UserId userId) {
		return users.findById(userId.value())
			.map(u -> new Profile(u.getId(), u.getEmail(), u.getDisplayName(), u.isDemo()))
			.orElseThrow(() -> new NotFoundException("User"));
	}

	public record Profile(UUID id, String email, String displayName, boolean demo) {
	}

}
