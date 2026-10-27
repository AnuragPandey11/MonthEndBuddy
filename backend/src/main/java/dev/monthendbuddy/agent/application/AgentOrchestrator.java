package dev.monthendbuddy.agent.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import dev.monthendbuddy.agent.application.ConversationService.HistoryMessage;
import dev.monthendbuddy.agent.domain.AgentConversation;
import dev.monthendbuddy.agent.domain.AgentMessage;
import dev.monthendbuddy.agent.domain.AgentStatus;
import dev.monthendbuddy.agent.llm.LlmGateway;
import dev.monthendbuddy.agent.llm.LlmMessage;
import dev.monthendbuddy.agent.llm.LlmReply;
import dev.monthendbuddy.agent.llm.LlmRequest;
import dev.monthendbuddy.agent.llm.ToolCall;
import dev.monthendbuddy.agent.tools.AskClarifyingQuestionTool;
import dev.monthendbuddy.agent.tools.AskClarifyingQuestionTool.Clarification;
import dev.monthendbuddy.agent.tools.ToolContext;
import dev.monthendbuddy.agent.tools.ToolExecution;
import dev.monthendbuddy.agent.tools.ToolRegistry;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.ProviderUnavailableException;
import dev.monthendbuddy.goals.SavingsProjectionService;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.identity.UserSettingsView;

/**
 * Runs one copilot turn (LLD §8.1, architecture §10.3). It runs the tool loop itself, so it can cap iterations and
 * tool calls, enforce the turn deadline, and record every call. The model only ever sees tool results; it cannot
 * change data, and the user ID comes from the caller, never from the model.
 * <p>
 * Not transactional: each database step is its own short transaction, so none stays open during a model call.
 */
@Service
@EnableConfigurationProperties(AgentProperties.class)
public class AgentOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

	private final LlmGateway llm;

	private final ToolRegistry tools;

	private final PromptBuilder prompts;

	private final ResponseAssembler assembler;

	private final ConversationService conversations;

	private final ProviderBudget budget;

	private final UserSettingsQuery settings;

	private final SavingsProjectionService goals;

	private final AgentProperties properties;

	private final JsonMapper json;

	private final Clock clock;

	AgentOrchestrator(LlmGateway llm, ToolRegistry tools, PromptBuilder prompts, ResponseAssembler assembler,
			ConversationService conversations, ProviderBudget budget, UserSettingsQuery settings,
			SavingsProjectionService goals, AgentProperties properties, JsonMapper json, Clock clock) {
		this.llm = llm;
		this.tools = tools;
		this.prompts = prompts;
		this.assembler = assembler;
		this.conversations = conversations;
		this.budget = budget;
		this.settings = settings;
		this.goals = goals;
		this.properties = properties;
		this.json = json;
		this.clock = clock;
	}

	public AgentResponse run(UserId user, UUID conversationId, String message, AgentEventSink sink) {
		long started = System.nanoTime();
		UserSettingsView userSettings = settings.get(user);
		ToolContext context = new ToolContext(user, userSettings.timezone(), userSettings.baseCurrency(),
				userSettings.today(clock));
		List<HistoryMessage> history = conversationId == null ? List.of()
				: conversations.history(user, conversationId, properties.historyTurns());
		AgentConversation conversation = conversations.appendUser(user, conversationId, message);

		TurnState state = new TurnState(
				prompts.build(context, goals.requiredMonthlyForActiveGoals(user, null), history, message),
				clock.instant().plus(properties.turnTimeout()));
		AgentStatus status = loop(state, context, sink);

		AgentResponse response = assembler.assemble(state, status, message);
		int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000);
		AgentMessage saved = conversations.appendAssistant(user, conversation.getId(),
				new AgentMessage.AssistantTurn(response.answer(), status, json.writeValueAsString(response.stored()),
						response.model(), response.promptVersion(), state.promptTokens(), state.completionTokens(),
						latencyMs));
		response = response.withIds(conversation.getId(), saved.getId());
		// After persisting, so a client that disconnected finds the answer when it reopens the conversation.
		sink.completed(response);
		// Counts and names only: never prompts, arguments or results (architecture §15).
		log.info("Agent turn status={} iterations={} tools={} promptTokens={} completionTokens={} latencyMs={}",
				status, state.iterations(), state.executions().stream().map(e -> e.call().name()).toList(),
				state.promptTokens(), state.completionTokens(), latencyMs);
		return response;
	}

	private AgentStatus loop(TurnState state, ToolContext context, AgentEventSink sink) {
		int toolCalls = 0;
		while (true) {
			if (state.iterations() >= properties.maxToolIterations()) {
				return AgentStatus.LIMIT_REACHED;
			}
			if (pastDeadline(state)) {
				return AgentStatus.PARTIAL;
			}
			LlmReply reply;
			try {
				LlmRequest request = new LlmRequest(state.messages(), tools.specs());
				long reserved = budget.reserve(request, state.iterations() == 0);
				reply = llm.complete(request);
				budget.settle(reserved, reply);
			}
			catch (ProviderUnavailableException e) {
				state.providerProblem(e.getMessage());
				return AgentStatus.PROVIDER_UNAVAILABLE;
			}
			state.recordReply(reply);

			if (!reply.hasToolCalls()) {
				if (reply.text() == null || reply.text().isBlank()) {
					return AgentStatus.PARTIAL;
				}
				state.finalText(reply.text());
				return AgentStatus.COMPLETE;
			}
			if (toolCalls + reply.toolCalls().size() > properties.maxToolCallsPerTurn()) {
				return AgentStatus.LIMIT_REACHED;
			}
			if (pastDeadline(state)) {
				return AgentStatus.PARTIAL;
			}
			List<ToolCall> calls = reply.toolCalls().stream().map(AgentOrchestrator::withId).toList();
			state.messages().add(new LlmMessage.Assistant(reply.text(), calls));
			for (ToolCall call : calls) {
				sink.toolStarted(call.id(), call.name(), tools.statusLabel(call.name()));
				ToolExecution execution = tools.execute(call, context);
				sink.toolFinished(call.id(), call.name(), execution.ok(), execution.durationMillis());
				state.executions().add(execution);
				toolCalls++;
				if (AskClarifyingQuestionTool.NAME.equals(call.name()) && execution.ok()) {
					state.clarification((Clarification) execution.result().data());
					return AgentStatus.NEEDS_CLARIFICATION;
				}
				state.messages()
					.add(new LlmMessage.ToolResult(call.id(), call.name(), json.writeValueAsString(execution.payload())));
			}
		}
	}

	private boolean pastDeadline(TurnState state) {
		return clock.instant().isAfter(state.deadline());
	}

	/** Every call needs an ID so its result can be matched to it; a provider that omits one gets one here. */
	private static ToolCall withId(ToolCall call) {
		return call.id() == null || call.id().isBlank()
				? new ToolCall("call_" + UUID.randomUUID().toString().substring(0, 8), call.name(), call.argumentsJson())
				: call;
	}

}
