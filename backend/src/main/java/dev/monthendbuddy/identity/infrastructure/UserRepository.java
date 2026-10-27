package dev.monthendbuddy.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import dev.monthendbuddy.identity.domain.User;

public interface UserRepository extends Repository<User, UUID> {

	Optional<User> findById(UUID id);

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	User saveAndFlush(User user);

}
