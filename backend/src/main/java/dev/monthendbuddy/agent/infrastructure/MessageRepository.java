package dev.monthendbuddy.agent.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;

import dev.monthendbuddy.agent.domain.AgentMessage;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.persistence.UserOwnedRepository;

public interface MessageRepository extends UserOwnedRepository<AgentMessage> {

	List<AgentMessage> findByConversationIdAndUserIdOrderByCreatedAtAscIdAsc(UUID conversationId, UserId userId);

	/** Newest first; callers reverse the list. */
	List<AgentMessage> findByConversationIdAndUserIdOrderByCreatedAtDescIdDesc(UUID conversationId, UserId userId,
			Limit limit);

}
