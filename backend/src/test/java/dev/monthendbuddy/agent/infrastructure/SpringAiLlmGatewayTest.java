package dev.monthendbuddy.agent.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.openai.core.http.Headers;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;

import dev.monthendbuddy.agent.llm.LlmGateway.ProviderState;
import dev.monthendbuddy.agent.llm.LlmMessage;
import dev.monthendbuddy.agent.llm.LlmReply;
import dev.monthendbuddy.agent.llm.LlmRequest;
import dev.monthendbuddy.agent.llm.ToolCall;
import dev.monthendbuddy.agent.llm.ToolSpec;
import dev.monthendbuddy.common.error.ProviderUnavailableException;

class SpringAiLlmGatewayTest {

	record Input(String month) {
	}

	private final OpenAiChatModel chatModel = mock(OpenAiChatModel.class);

	private final LlmRequest request = new LlmRequest(List.of(new LlmMessage.User("hi")),
			List.of(new ToolSpec("getMonthlySummary", "Summary", Input.class)));

	@BeforeEach
	void configuredModel() {
		when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("configured-model").build());
	}

	@Test
	void blankKeyFailsWithoutCallingTheProvider() {
		SpringAiLlmGateway gateway = new SpringAiLlmGateway(chatModel, " ", Duration.ofSeconds(20));
		assertThat(gateway.state()).isEqualTo(ProviderState.NOT_CONFIGURED);
		assertThatThrownBy(() -> gateway.complete(request)).isInstanceOf(ProviderUnavailableException.class);
		verify(chatModel, never()).call(any(Prompt.class));
	}

	@Test
	void requestKeepsTheConfiguredModelAndOffersTheTools() {
		when(chatModel.call(any(Prompt.class))).thenReturn(reply("Hello"));
		LlmReply reply = gateway().complete(request);
		assertThat(reply.text()).isEqualTo("Hello");

		ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel).call(prompt.capture());
		OpenAiChatOptions options = (OpenAiChatOptions) prompt.getValue().getOptions();
		assertThat(options.getModel()).isEqualTo("configured-model");
		assertThat(options.getToolCallbacks()).singleElement()
			.satisfies(t -> assertThat(t.getToolDefinition().inputSchema()).contains("month"));
	}

	@Test
	void serverErrorIsRetriedOnce() {
		when(chatModel.call(any(Prompt.class))).thenThrow(serverError()).thenReturn(reply("ok"));
		assertThat(gateway().complete(request).text()).isEqualTo("ok");
		verify(chatModel, times(2)).call(any(Prompt.class));
	}

	@Test
	void twoFailuresBecomeProviderUnavailable() {
		when(chatModel.call(any(Prompt.class))).thenThrow(serverError()).thenThrow(new OpenAIIoException("reset"));
		assertThatThrownBy(() -> gateway().complete(request)).isInstanceOf(ProviderUnavailableException.class);
	}

	@Test
	void longRetryAfterFailsFastWithoutRetrying() {
		when(chatModel.call(any(Prompt.class))).thenThrow(RateLimitException.builder()
			.headers(Headers.builder().put("retry-after", "60").build())
			.build());
		assertThatThrownBy(() -> gateway().complete(request)).isInstanceOf(ProviderUnavailableException.class);
		verify(chatModel, times(1)).call(any(Prompt.class));
	}

	@Test
	void unexpectedClientExceptionNeverEscapes() {
		when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalArgumentException("odd"));
		assertThatThrownBy(() -> gateway().complete(request)).isInstanceOf(ProviderUnavailableException.class);
	}

	@Test
	void circuitOpensAfterRepeatedFailuresAndThenFailsFast() {
		when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalArgumentException("down"));
		SpringAiLlmGateway gateway = gateway();
		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> gateway.complete(request)).isInstanceOf(ProviderUnavailableException.class);
		}
		assertThat(gateway.state()).isEqualTo(ProviderState.CIRCUIT_OPEN);
		assertThatThrownBy(() -> gateway.complete(request)).isInstanceOf(ProviderUnavailableException.class);
		verify(chatModel, times(5)).call(any(Prompt.class));
	}

	@Test
	void assistantMessagesAreRebuiltWithoutProviderMetadataAndToolResultsAreGrouped() {
		List<Message> messages = SpringAiLlmGateway.toSpringMessages(List.of(new LlmMessage.User("q"),
				new LlmMessage.Assistant(null, List.of(new ToolCall("a", "t1", "{}"), new ToolCall("b", "t2", "{}"))),
				new LlmMessage.ToolResult("a", "t1", "{\"x\":1}"), new LlmMessage.ToolResult("b", "t2", "{\"y\":2}"),
				new LlmMessage.User("next")));
		assertThat(messages).hasSize(4);
		AssistantMessage assistant = (AssistantMessage) messages.get(1);
		assertThat(assistant.getToolCalls()).hasSize(2);
		assertThat(assistant.getMetadata()).doesNotContainKey("reasoningContent");
		assertThat(((ToolResponseMessage) messages.get(2)).getResponses()).extracting(ToolResponseMessage.ToolResponse::id)
			.containsExactly("a", "b");
	}

	private SpringAiLlmGateway gateway() {
		return new SpringAiLlmGateway(chatModel, "test-key", Duration.ofSeconds(20));
	}

	private static ChatResponse reply(String text) {
		return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(text).build())));
	}

	private static InternalServerException serverError() {
		return InternalServerException.builder().statusCode(503).headers(Headers.builder().build()).build();
	}

}
