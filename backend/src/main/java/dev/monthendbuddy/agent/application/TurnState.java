package dev.monthendbuddy.agent.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import dev.monthendbuddy.agent.llm.LlmMessage;
import dev.monthendbuddy.agent.llm.LlmReply;
import dev.monthendbuddy.agent.tools.AskClarifyingQuestionTool;
import dev.monthendbuddy.agent.tools.AskClarifyingQuestionTool.Clarification;
import dev.monthendbuddy.agent.tools.ToolExecution;

/** The mutable state of one turn, owned by a single orchestrator call. */
class TurnState {

	private final List<LlmMessage> messages;

	private final List<ToolExecution> executions = new ArrayList<>();

	private final Instant deadline;

	private int iterations;

	private int promptTokens;

	private int completionTokens;

	private String model;

	private String finalText;

	private Clarification clarification;

	private String providerProblem;

	TurnState(List<LlmMessage> messages, Instant deadline) {
		this.messages = new ArrayList<>(messages);
		this.deadline = deadline;
	}

	void recordReply(LlmReply reply) {
		iterations++;
		promptTokens += reply.promptTokens();
		completionTokens += reply.completionTokens();
		if (reply.model() != null) {
			model = reply.model();
		}
	}

	boolean isClarification(ToolExecution execution) {
		return AskClarifyingQuestionTool.NAME.equals(execution.call().name());
	}

	List<LlmMessage> messages() {
		return messages;
	}

	List<ToolExecution> executions() {
		return executions;
	}

	Instant deadline() {
		return deadline;
	}

	int iterations() {
		return iterations;
	}

	int promptTokens() {
		return promptTokens;
	}

	int completionTokens() {
		return completionTokens;
	}

	String model() {
		return model;
	}

	String finalText() {
		return finalText;
	}

	void finalText(String text) {
		this.finalText = text;
	}

	Clarification clarification() {
		return clarification;
	}

	void clarification(Clarification value) {
		this.clarification = value;
	}

	String providerProblem() {
		return providerProblem;
	}

	void providerProblem(String problem) {
		this.providerProblem = problem;
	}

}
