package dev.monthendbuddy.agent.infrastructure;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.monthendbuddy.agent.domain.AgentConversation;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.persistence.UserOwnedRepository;

public interface ConversationRepository extends UserOwnedRepository<AgentConversation> {

	List<AgentConversation> findByUserIdOrderByUpdatedAtDesc(UserId userId, Limit limit);

	@Modifying
	@Query("delete from AgentConversation c where c.userId = :user")
	int deleteAllForDemoReset(@Param("user") UserId user);

}
