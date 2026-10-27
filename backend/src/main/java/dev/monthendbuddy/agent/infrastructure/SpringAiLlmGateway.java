package dev.monthendbuddy.agent.infrastructure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import dev.monthendbuddy.agent.application.AgentProperties;
import dev.monthendbuddy.agent.llm.LlmGateway;
import dev.monthendbuddy.agent.llm.LlmMessage;
import dev.monthendbuddy.agent.llm.LlmReply;
import dev.monthendbuddy.agent.llm.LlmRequest;
import dev.monthendbuddy.agent.llm.ToolCall;
import dev.monthendbuddy.agent.llm.ToolSpec;
import dev.monthendbuddy.common.error.ProviderUnavailableException;

/**
 * The only class that uses Spring AI (architecture §10.2). Its rules come from failures seen in the provider spike
 * (ADR 0001):
 * <ul>
 * <li>It calls {@code ChatModel} directly, never {@code ChatClient}: in Spring AI 2.0 only ChatClient's advisor runs
 * tools, so the model's tool calls come back to us unexecuted and the orchestrator runs the loop.</li>
 * <li>Per-request options are built from the configured ones ({@code getOptions().mutate()}); options built from
 * scratch replace the configured model.</li>
 * <li>Messages are rebuilt from our own types on every call, so the model's reasoning metadata is never replayed
 * (Groq rejects it).</li>
 * <li>Tools are definition-only callbacks that refuse to run.</li>
 * <li>The SDK's own retries are off; this class retries once (architecture §10.8) and a circuit breaker fails fast
 * after repeated failures.</li>
 * </ul>
 * Nothing here logs prompts or tool payloads (architecture §15). Chosen by {@code meb.agent.provider=groq}, the
 * default.
 */
@Component
@ConditionalOnProperty(name = "meb.agent.provider", havingValue = "groq", matchIfMissing = true)
public class SpringAiLlmGateway implements LlmGateway {

	private static final Logger log = LoggerFactory.getLogger(SpringAiLlmGateway.class);

	private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(5);

	private final OpenAiChatModel chatModel;

	private final boolean configured;

	private final Duration timeout;

	private final CircuitBreaker breaker;

	@Autowired
	SpringAiLlmGateway(OpenAiChatModel chatModel, @Value("${spring.ai.openai.api-key:}") String apiKey,
			AgentProperties properties) {
		this(chatModel, apiKey, properties.modelTimeout());
	}

	SpringAiLlmGateway(OpenAiChatModel chatModel, String apiKey, Duration timeout) {
		this.chatModel = chatModel;
		this.configured = apiKey != null && !apiKey.isBlank();
		this.timeout = timeout;
		this.breaker = CircuitBreaker.of("aiProvider", CircuitBreakerConfig.custom()
			.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
			.slidingWindowSize(60)
			.minimumNumberOfCalls(5)
			.failureRateThreshold(50)
			.waitDurationInOpenState(Duration.ofSeconds(30))
			.recordExceptions(ProviderUnavailableException.class)
			.build());
	}

	@Override
	public ProviderState state() {
		if (!configured) {
			return ProviderState.NOT_CONFIGURED;
		}
		return breaker.getState() == CircuitBreaker.State.OPEN ? ProviderState.CIRCUIT_OPEN : ProviderState.AVAILABLE;
	}

	@Override
	public LlmReply complete(LlmRequest request) {
		if (!configured) {
			throw new ProviderUnavailableException("No AI provider key is configured");
		}
		try {
			return breaker.executeSupplier(() -> callWithOneRetry(request));
		}
		catch (CallNotPermittedException e) {
			throw new ProviderUnavailableException("The AI provider is failing; trying again shortly");
		}
	}

	private LlmReply callWithOneRetry(LlmRequest request) {
		Prompt prompt = new Prompt(toSpringMessages(request.messages()), options(request.tools()));
		try {
			return call(prompt);
		}
		catch (RateLimitException e) {
			Duration wait = retryAfter(e).orElse(Duration.ofSeconds(1));
			if (wait.compareTo(MAX_RETRY_AFTER) > 0) {
				throw new ProviderUnavailableException("The AI provider is rate limiting requests");
			}
			sleep(wait);
			return retry(prompt, e);
		}
		catch (OpenAIServiceException e) {
			if (e.statusCode() >= 500) {
				sleep(Duration.ofMillis(300 + ThreadLocalRandom.current().nextInt(700)));
				return retry(prompt, e);
			}
			log.warn("AI provider rejected the request with status {}", e.statusCode());
			throw new ProviderUnavailableException("The AI provider rejected the request");
		}
		catch (OpenAIIoException e) {
			sleep(Duration.ofMillis(300 + ThreadLocalRandom.current().nextInt(700)));
			return retry(prompt, e);
		}
		catch (RuntimeException e) {
			// Anything else from the client library: never let it escape as a 500.
			log.warn("AI provider call failed: {}", e.getClass().getSimpleName());
			throw new ProviderUnavailableException("The AI provider is unavailable");
		}
	}

	private LlmReply retry(Prompt prompt, RuntimeException first) {
		try {
			return call(prompt);
		}
		catch (RuntimeException e) {
			log.warn("AI provider call failed twice: {} then {}", first.getClass().getSimpleName(),
					e.getClass().getSimpleName());
			throw new ProviderUnavailableException("The AI provider is unavailable");
		}
	}

	private LlmReply call(Prompt prompt) {
		ChatResponse response = chatModel.call(prompt);
		AssistantMessage out = response.getResult().getOutput();
		List<ToolCall> calls = out.getToolCalls()
			.stream()
			.map(c -> new ToolCall(c.id(), c.name(), c.arguments()))
			.toList();
		var usage = response.getMetadata().getUsage();
		return new LlmReply(out.getText(), calls, usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
				usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens(), response.getMetadata().getModel());
	}

	private OpenAiChatOptions options(List<ToolSpec> tools) {
		List<ToolCallback> callbacks = tools.stream().map(DefinitionOnlyTool::new).map(ToolCallback.class::cast).toList();
		return chatModel.getOptions().mutate().toolCallbacks(callbacks).maxRetries(0).timeout(timeout).build();
	}

	static List<Message> toSpringMessages(List<LlmMessage> messages) {
		List<Message> result = new ArrayList<>();
		List<ToolResponseMessage.ToolResponse> pendingResults = new ArrayList<>();
		for (LlmMessage message : messages) {
			if (!(message instanceof LlmMessage.ToolResult) && !pendingResults.isEmpty()) {
				result.add(ToolResponseMessage.builder().responses(List.copyOf(pendingResults)).build());
				pendingResults.clear();
			}
			switch (message) {
				case LlmMessage.System s -> result.add(new SystemMessage(s.text()));
				case LlmMessage.User u -> result.add(new UserMessage(u.text()));
				case LlmMessage.Assistant a -> result.add(AssistantMessage.builder()
					.content(a.text() == null ? "" : a.text())
					.toolCalls(a.toolCalls()
						.stream()
						.map(c -> new AssistantMessage.ToolCall(c.id(), "function", c.name(), c.argumentsJson()))
						.toList())
					.build());
				case LlmMessage.ToolResult t -> pendingResults
					.add(new ToolResponseMessage.ToolResponse(t.toolCallId(), t.toolName(), t.json()));
			}
		}
		if (!pendingResults.isEmpty()) {
			result.add(ToolResponseMessage.builder().responses(List.copyOf(pendingResults)).build());
		}
		return result;
	}

	private static Optional<Duration> retryAfter(OpenAIServiceException e) {
		try {
			return e.headers().values("retry-after").stream().findFirst().map(v -> Duration.ofSeconds(Long.parseLong(v.trim())));
		}
		catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ProviderUnavailableException("Interrupted while waiting to retry");
		}
	}

	/** Gives the model a tool's name, description and schema; the orchestrator, not Spring AI, executes the call. */
	static final class DefinitionOnlyTool implements ToolCallback {

		private final ToolDefinition definition;

		DefinitionOnlyTool(ToolSpec spec) {
			this.definition = DefaultToolDefinition.builder()
				.name(spec.name())
				.description(spec.description())
				.inputSchema(JsonSchemaGenerator.generateForType(spec.inputType()))
				.build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return definition;
		}

		@Override
		public String call(String toolInput) {
			throw new IllegalStateException("Tools are executed by AgentOrchestrator, not by Spring AI");
		}

	}

}
