package dev.monthendbuddy.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import dev.monthendbuddy.common.TextSanitizer;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.persistence.UserOwnedEntity;

/** One copilot thread. Its title is the first message, truncated; no model call is spent on it (architecture §10.9). */
@Entity
@Table(name = "agent_conversations")
public class AgentConversation extends UserOwnedEntity {

	static final int TITLE_LENGTH = 80;

	@Column(name = "title", nullable = false)
	private String title;

	protected AgentConversation() {
	}

	public static AgentConversation start(UserId owner, String firstMessage) {
		AgentConversation conversation = new AgentConversation();
		conversation.userId = owner;
		String cleaned = TextSanitizer.singleLine(firstMessage);
		if (cleaned == null) {
			cleaned = "New conversation";
		}
		conversation.title = cleaned.length() <= TITLE_LENGTH ? cleaned
				: cleaned.substring(0, TITLE_LENGTH - 1).strip() + "…";
		return conversation;
	}

	/** Marks the conversation as active now, so the list shows the most recent first. */
	public void touch() {
		this.updatedAt = null;
	}

	public String getTitle() {
		return title;
	}

}
