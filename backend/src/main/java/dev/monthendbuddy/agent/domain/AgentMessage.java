package dev.monthendbuddy.agent.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dev.monthendbuddy.common.TextSanitizer;
import dev.monthendbuddy.common.persistence.UserOwnedEntity;

/**
 * One message in a conversation. An assistant message keeps its turn's evidence as JSON in {@code toolMetadata}, so
 * a reopened conversation re-renders its cards without calling any tool again (architecture §10.9). Messages are
 * never changed after they are written.
 */
@Entity
@Table(name = "agent_messages")
public class AgentMessage extends UserOwnedEntity {

	@Column(name = "conversation_id", nullable = false, updatable = false)
	private UUID conversationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, updatable = false)
	private MessageRole role;

	@Column(name = "content", nullable = false, updatable = false)
	private String content;

	@Column(name = "tool_metadata", updatable = false)
	private String toolMetadata;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", updatable = false)
	private AgentStatus status;

	@Column(name = "model", updatable = false)
	private String model;

	@Column(name = "prompt_version", updatable = false)
	private String promptVersion;

	@Column(name = "prompt_tokens", updatable = false)
	private Integer promptTokens;

	@Column(name = "completion_tokens", updatable = false)
	private Integer completionTokens;

	@Column(name = "latency_ms", updatable = false)
	private Integer latencyMs;

	protected AgentMessage() {
	}

	public static AgentMessage user(AgentConversation conversation, String text) {
		AgentMessage message = new AgentMessage();
		message.userId = conversation.getUserId();
		message.conversationId = conversation.getId();
		message.role = MessageRole.USER;
		message.content = TextSanitizer.multiLine(text);
		return message;
	}

	public static AgentMessage assistant(AgentConversation conversation, AssistantTurn turn) {
		AgentMessage message = new AgentMessage();
		message.userId = conversation.getUserId();
		message.conversationId = conversation.getId();
		message.role = MessageRole.ASSISTANT;
		message.content = turn.answer() == null ? "" : turn.answer();
		message.toolMetadata = turn.toolMetadataJson();
		message.status = turn.status();
		message.model = turn.model();
		message.promptVersion = turn.promptVersion();
		message.promptTokens = turn.promptTokens();
		message.completionTokens = turn.completionTokens();
		message.latencyMs = turn.latencyMs();
		return message;
	}

	/** What is stored for one assistant turn. */
	public record AssistantTurn(String answer, AgentStatus status, String toolMetadataJson, String model,
			String promptVersion, int promptTokens, int completionTokens, int latencyMs) {
	}

	public UUID getConversationId() {
		return conversationId;
	}

	public MessageRole getRole() {
		return role;
	}

	public String getContent() {
		return content;
	}

	public String getToolMetadata() {
		return toolMetadata;
	}

	public AgentStatus getStatus() {
		return status;
	}

	public String getModel() {
		return model;
	}

	public String getPromptVersion() {
		return promptVersion;
	}

}
