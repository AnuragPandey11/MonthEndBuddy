package dev.monthendbuddy.agent.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.agent.llm.LlmMessage;
import dev.monthendbuddy.agent.llm.LlmReply;
import dev.monthendbuddy.agent.llm.LlmRequest;
import dev.monthendbuddy.common.error.ProviderUnavailableException;

/** The offline demo script is opt-in: the test gateway still reports "unavailable" with nothing queued. */
class ScriptedLlmGatewayTest {

	private static LlmRequest ask(LlmMessage... messages) {
		return new LlmRequest(List.of(messages), List.of());
	}

	@Test
	void withoutTheDemoNothingQueuedMeansUnavailable() {
		assertThatThrownBy(() -> new ScriptedLlmGateway().complete(ask(new LlmMessage.User("Hi"))))
			.isInstanceOf(ProviderUnavailableException.class);
	}

	@Test
	void theDemoLooksUpTheSummaryThenAnswers() {
		ScriptedLlmGateway demo = new ScriptedLlmGateway(Duration.ZERO);

		LlmReply first = demo.complete(ask(new LlmMessage.User("How did this month go?")));
		assertThat(first.toolCalls()).singleElement()
			.satisfies(call -> assertThat(call.name()).isEqualTo("getMonthlySummary"));

		LlmReply answer = demo.complete(ask(new LlmMessage.User("How did this month go?"),
				new LlmMessage.Assistant(null, first.toolCalls()),
				new LlmMessage.ToolResult("demo_1", "getMonthlySummary", "{}")));
		assertThat(answer.hasToolCalls()).isFalse();
		assertThat(answer.text()).isEqualTo(ScriptedLlmGateway.DEMO_ANSWER);
	}

	@Test
	void aQueuedStepStillWinsOverTheDemo() {
		ScriptedLlmGateway demo = new ScriptedLlmGateway(Duration.ZERO);
		demo.thenReply(new LlmReply("Queued.", List.of(), 1, 1, "scripted"));
		assertThat(demo.complete(ask(new LlmMessage.User("Hi"))).text()).isEqualTo("Queued.");
	}

}
