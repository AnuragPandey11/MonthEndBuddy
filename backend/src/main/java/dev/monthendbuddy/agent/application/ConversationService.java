package dev.monthendbuddy.agent.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.monthendbuddy.agent.domain.AgentConversation;
import dev.monthendbuddy.agent.domain.AgentMessage;
import dev.monthendbuddy.agent.domain.AgentStatus;
import dev.monthendbuddy.agent.domain.MessageRole;
import dev.monthendbuddy.agent.infrastructure.ConversationRepository;
import dev.monthendbuddy.agent.infrastructure.MessageRepository;
import dev.monthendbuddy.common.DemoDataReset;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.NotFoundException;

/**
 * Stores conversations (LLD §8.3). Every method is its own short transaction: the orchestrator calls them between
 * model calls, so no database transaction stays open while the model is thinking.
 */
@Service
public class ConversationService implements DemoDataReset {

	static final int LIST_LIMIT = 50;

	private final ConversationRepository conversations;

	private final MessageRepository messages;

	private final JsonMapper json;

	ConversationService(ConversationRepository conversations, MessageRepository messages, JsonMapper json) {
		this.conversations = conversations;
		this.messages = messages;
		this.json = json;
	}

	/** The caller's conversation; another user's is reported as missing (architecture §5.3). */
	@Transactional(readOnly = true)
	public AgentConversation require(UserId user, UUID conversationId) {
		return conversations.findByIdAndUserId(conversationId, user)
			.orElseThrow(() -> new NotFoundException("Conversation"));
	}

	/** Appends the user's message to the given conversation, or to a new one titled after it. */
	@Transactional
	public AgentConversation appendUser(UserId user, UUID conversationId, String text) {
		AgentConversation conversation = conversationId == null
				? conversations.saveAndFlush(AgentConversation.start(user, text)) : require(user, conversationId);
		conversation.touch();
		messages.save(AgentMessage.user(conversation, text));
		return conversation;
	}

	@Transactional
	public AgentMessage appendAssistant(UserId user, UUID conversationId, AgentMessage.AssistantTurn turn) {
		AgentConversation conversation = require(user, conversationId);
		conversation.touch();
		return messages.saveAndFlush(AgentMessage.assistant(conversation, turn));
	}

	/**
	 * The last {@code turns} user/assistant pairs, oldest first; call it before appending the new message. Each assistant message
	 * comes back as text plus a one-line note of the tools it used; earlier tool results are not re-sent
	 * (architecture §10.9). Turns the provider never answered are skipped.
	 */
	@Transactional(readOnly = true)
	public List<HistoryMessage> history(UserId user, UUID conversationId, int turns) {
		List<AgentMessage> newestFirst = messages.findByConversationIdAndUserIdOrderByCreatedAtDescIdDesc(
				conversationId, user, Limit.of(turns * 2));
		List<HistoryMessage> history = new ArrayList<>();
		for (int i = newestFirst.size() - 1; i >= 0; i--) {
			AgentMessage message = newestFirst.get(i);
			if (message.getRole() == MessageRole.ASSISTANT && message.getStatus() == AgentStatus.PROVIDER_UNAVAILABLE) {
				continue;
			}
			history.add(new HistoryMessage(message.getRole(), message.getContent(), toolsUsed(message)));
		}
		return history;
	}

	@Transactional(readOnly = true)
	public List<ConversationSummary> list(UserId user) {
		return conversations.findByUserIdOrderByUpdatedAtDesc(user, Limit.of(LIST_LIMIT))
			.stream()
			.map(c -> new ConversationSummary(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()))
			.toList();
	}

	@Transactional(readOnly = true)
	public ConversationView get(UserId user, UUID conversationId) {
		AgentConversation conversation = require(user, conversationId);
		List<MessageView> views = messages
			.findByConversationIdAndUserIdOrderByCreatedAtAscIdAsc(conversationId, user)
			.stream()
			.map(m -> new MessageView(m.getId(), m.getRole(), m.getContent(), m.getStatus(), metadata(m),
					m.getModel(), m.getPromptVersion(), m.getCreatedAt()))
			.toList();
		return new ConversationView(conversation.getId(), conversation.getTitle(), conversation.getCreatedAt(),
				conversation.getUpdatedAt(), views);
	}

	/** Messages go with it ({@code ON DELETE CASCADE}). */
	@Transactional
	public void delete(UserId user, UUID conversationId) {
		if (conversations.deleteByIdAndUserId(conversationId, user) == 0) {
			throw new NotFoundException("Conversation");
		}
	}

	@Override
	@Transactional
	public void deleteDemoData(UserId demoUser) {
		conversations.deleteAllForDemoReset(demoUser);
	}

	private JsonNode metadata(AgentMessage message) {
		return message.getToolMetadata() == null ? null : json.readTree(message.getToolMetadata());
	}

	private String toolsUsed(AgentMessage message) {
		JsonNode metadata = metadata(message);
		if (metadata == null || !metadata.has("evidence")) {
			return null;
		}
		List<String> calls = new ArrayList<>();
		for (JsonNode evidence : metadata.get("evidence")) {
			calls.add(evidence.get("tool").asString() + json.writeValueAsString(evidence.get("args")));
		}
		return calls.isEmpty() ? null : String.join(", ", calls);
	}

	/** One earlier message as the model sees it again. {@code toolsUsed} is null for user messages. */
	public record HistoryMessage(MessageRole role, String content, String toolsUsed) {
	}

	public record ConversationSummary(UUID id, String title, Instant createdAt, Instant updatedAt) {
	}

	public record ConversationView(UUID id, String title, Instant createdAt, Instant updatedAt,
			List<MessageView> messages) {
	}

	/** {@code metadata} is the stored evidence snapshot of an assistant turn ({@code null} for user messages). */
	public record MessageView(UUID id, MessageRole role, String content, AgentStatus status, JsonNode metadata,
			String model, String promptVersion, Instant createdAt) {
	}

}
