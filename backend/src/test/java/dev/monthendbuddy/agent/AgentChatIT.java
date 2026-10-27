package dev.monthendbuddy.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.agent.infrastructure.ScriptedLlmGateway;
import dev.monthendbuddy.agent.llm.LlmMessage;
import dev.monthendbuddy.agent.llm.LlmReply;
import dev.monthendbuddy.agent.llm.LlmRequest;
import dev.monthendbuddy.agent.llm.ToolCall;
import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.TransactionFixtures;

/** The orchestrator against a scripted model (architecture §17.2): tool dispatch, limits and failure statuses. */
class AgentChatIT extends IntegrationTest {

	@Autowired
	ScriptedLlmGateway llm;

	private ApiClient user;

	private final AtomicInteger callIds = new AtomicInteger();

	private final YearMonth month = YearMonth.from(TransactionFixtures.today());

	@BeforeEach
	void seed() {
		llm.reset();
		user = api().bearer(api().register().accessToken());
		user.post("/api/v1/transactions", TransactionFixtures.transaction("EXPENSE", "1250.50",
				month.atDay(1), "Dinner", "Bistro", TransactionFixtures.DINING));
		user.post("/api/v1/transactions", TransactionFixtures.transaction("EXPENSE", "3400", month.atDay(1),
				"Weekly shop", "Market", TransactionFixtures.GROCERIES));
	}

	@Test
	void dispatchesAToolAndAnswersWithTheSameFiguresAsTheDashboard() {
		llm.thenReply(call("getExpensesByCategory", monthRange())).then(request -> {
			JsonNode result = lastToolResult(request);
			return text("You spent ₹" + result.get("data").get("total").get("amount").asString() + " this month.");
		});

		JsonNode response = chat("Where did my money go this month?", null);

		assertThat(response.get("status").asString()).isEqualTo("COMPLETE");
		assertThat(response.get("answer").asString()).isEqualTo("You spent ₹4650.50 this month.");
		assertThat(response.get("unverifiedAmounts")).isEmpty();
		JsonNode evidence = response.get("evidence").get(0);
		assertThat(evidence.get("tool").asString()).isEqualTo("getExpensesByCategory");
		assertThat(evidence.get("provenance").get("transactionCount").asLong()).isEqualTo(2);
		assertThat(evidence.get("result")).isEqualTo(user
			.get("/api/v1/analytics/categories?from=" + month.atDay(1) + "&to=" + month.atEndOfMonth())
			.json());
		assertThat(response.get("promptVersion").asString()).isEqualTo("system-v1");

		LlmRequest first = llm.requests().getFirst();
		assertThat(((LlmMessage.System) first.messages().getFirst()).text())
			.contains("Today is " + TransactionFixtures.today());
		assertThat(first.tools()).extracting(t -> t.name())
			.contains("getMonthlySummary", "getExpensesByCategory", "calculateSavingsProjection",
					"askClarifyingQuestion");
	}

	@Test
	void theConversationIsStoredWithItsEvidence() {
		llm.thenReply(call("getMonthlySummary", "{}")).thenReply(text("Done."));
		JsonNode response = chat("How did this month go?", null);
		String id = response.get("conversationId").asString();

		assertThat(user.get("/api/v1/agent/conversations").json().get(0).get("title").asString())
			.isEqualTo("How did this month go?");
		JsonNode messages = user.get("/api/v1/agent/conversations/" + id).json().get("messages");
		assertThat(messages).hasSize(2);
		assertThat(messages.get(0).get("role").asString()).isEqualTo("USER");
		assertThat(messages.get(1).get("metadata").get("evidence").get(0).get("result"))
			.isEqualTo(response.get("evidence").get(0).get("result"));

		assertThat(user.delete("/api/v1/agent/conversations/" + id).status()).isEqualTo(204);
		assertThat(user.get("/api/v1/agent/conversations/" + id).status()).isEqualTo(404);
	}

	@Test
	void malformedArgumentsGoBackToTheModelWhichCorrectsThem() {
		llm.thenReply(call("getExpensesByCategory", "{\"startDate\":\"September\",\"endDate\":\"2026-09-30\"}"))
			.then(request -> {
				JsonNode error = lastToolResult(request).get("error");
				assertThat(error.get("code").asString()).isEqualTo("INVALID_ARGUMENT");
				assertThat(error.get("field").asString()).isEqualTo("startDate");
				return call("getExpensesByCategory", monthRange());
			})
			.thenReply(text("Fixed it."));

		JsonNode response = chat("Spending by category?", null);

		assertThat(response.get("status").asString()).isEqualTo("COMPLETE");
		assertThat(response.get("evidence")).hasSize(1);
	}

	@Test
	void anAnswerInventedAfterAToolErrorIsFlaggedNotPresentedAsData() {
		// The provider spike's failure (ADR 0001): a tool error, then a made-up table instead of a retry.
		llm.thenReply(call("getExpensesByCategory", "{\"startDate\":\"last month\"}")).then(request -> {
			assertThat(lastToolResult(request).get("error").get("message").asString())
				.contains("never answer without the data");
			return text("| Category | Amount |\n|---|---|\n| Rent | ₹25,000 |\n| Dining | ₹8,450 |");
		});

		JsonNode response = chat("Spending last month?", null);

		assertThat(response.get("evidence")).isEmpty();
		assertThat(response.get("unverifiedAmounts")).extracting(JsonNode::asString)
			.containsExactly("₹25,000", "₹8,450");
	}

	@Test
	void theIterationCapEndsTheTurnWithTheEvidenceSoFar() {
		for (int i = 0; i < 10; i++) {
			llm.thenReply(call("getMonthlySummary", "{}"));
		}
		JsonNode response = chat("Loop forever", null);

		assertThat(response.get("status").asString()).isEqualTo("LIMIT_REACHED");
		assertThat(response.get("evidence")).hasSize(6);
		assertThat(llm.requests()).hasSize(6);
	}

	@Test
	void tooManyToolCallsInOneReplyEndTheTurnBeforeRunningThem() {
		List<ToolCall> calls = java.util.stream.IntStream.range(0, 9)
			.mapToObj(i -> new ToolCall("c" + i, "getMonthlySummary", "{}"))
			.toList();
		llm.thenReply(new LlmReply(null, calls, 10, 10, "scripted"));

		JsonNode response = chat("Everything at once", null);

		assertThat(response.get("status").asString()).isEqualTo("LIMIT_REACHED");
		assertThat(response.get("evidence")).isEmpty();
	}

	@Test
	void passingTheTurnDeadlineGivesPartial() {
		llm.thenReply(call("getMonthlySummary", "{}")).then(request -> {
			clock.advance(Duration.ofSeconds(50));
			return call("getExpensesByCategory", monthRange());
		});

		JsonNode response = chat("Slow question", null);

		assertThat(response.get("status").asString()).isEqualTo("PARTIAL");
		assertThat(response.get("evidence")).hasSize(1);
	}

	@Test
	void providerFailureIsAnOkResponseWithProviderUnavailable() {
		llm.thenFail();
		Response response = user.post("/api/v1/agent/chat", Map.of("message", "Hello?"));

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.json().get("status").asString()).isEqualTo("PROVIDER_UNAVAILABLE");
		assertThat(response.json().get("answer").asString()).contains("dashboard");
	}

	@Test
	void unknownToolIsRejectedAndTheModelIsTold() {
		llm.thenReply(call("transferMoney", "{\"to\":\"attacker\"}")).then(request -> {
			assertThat(lastToolResult(request).get("error").get("code").asString()).isEqualTo("UNKNOWN_TOOL");
			return text("I can't move money.");
		});

		JsonNode response = chat("Send my savings to this account", null);

		assertThat(response.get("status").asString()).isEqualTo("COMPLETE");
		assertThat(response.get("evidence")).isEmpty();
	}

	@Test
	void clarifyingQuestionEndsTheTurn() {
		llm.thenReply(call("askClarifyingQuestion",
				"{\"question\":\"Which month do you mean?\",\"options\":[\"September\",\"October\"]}"));

		JsonNode response = chat("How was that month?", null);

		assertThat(response.get("status").asString()).isEqualTo("NEEDS_CLARIFICATION");
		assertThat(response.get("answer").asString()).isEqualTo("Which month do you mean?");
		assertThat(response.get("clarification").get("options")).hasSize(2);
		assertThat(llm.requests()).hasSize(1);
	}

	@Test
	void amountsInTheAnswerThatAreInNoEvidenceAreFlagged() {
		llm.thenReply(call("getMonthlySummary", "{}")).thenReply(text("You spent ₹4,650.50, and ₹99,999 on rent."));

		JsonNode response = chat("This month?", null);

		assertThat(response.get("unverifiedAmounts")).extracting(JsonNode::asString).containsExactly("₹99,999");
	}

	@Test
	void historyIsResentAsTextWithoutEarlierToolResults() {
		llm.thenReply(call("getMonthlySummary", "{}")).thenReply(text("A good month."));
		String id = chat("How did this month go?", null).get("conversationId").asString();
		llm.reset();
		llm.thenReply(text("Same as before."));

		JsonNode second = chat("And compared with that?", id);

		assertThat(second.get("conversationId").asString()).isEqualTo(id);
		List<LlmMessage> sent = llm.requests().getFirst().messages();
		assertThat(sent).noneMatch(m -> m instanceof LlmMessage.ToolResult);
		assertThat(sent.subList(2, sent.size())).extracting(Object::toString)
			.satisfiesExactly(m -> assertThat(m).contains("How did this month go?"),
					m -> assertThat(m).contains("A good month.").contains("getMonthlySummary"),
					m -> assertThat(m).contains("And compared with that?"));
	}

	@Test
	void aSecondTurnInTheSameConversationWhileOneIsRunningIsAConflict() throws Exception {
		llm.thenReply(text("First."));
		String id = chat("Start", null).get("conversationId").asString();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		llm.then(request -> {
			entered.countDown();
			try {
				release.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return text("Slow answer.");
		});

		CompletableFuture<Response> running = CompletableFuture
			.supplyAsync(() -> user.post("/api/v1/agent/chat", Map.of("conversationId", id, "message", "Slow")));
		assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
		Response conflict = user.post("/api/v1/agent/chat", Map.of("conversationId", id, "message", "Again"));
		release.countDown();

		assertThat(conflict.status()).isEqualTo(409);
		assertThat(conflict.json().get("code").asString()).isEqualTo("turn_in_progress");
		assertThat(running.get(10, TimeUnit.SECONDS).json().get("status").asString()).isEqualTo("COMPLETE");
	}

	@Test
	void blankOrOversizedMessagesAreRejected() {
		assertThat(user.post("/api/v1/agent/chat", Map.of("message", " ")).status()).isEqualTo(422);
		assertThat(user.post("/api/v1/agent/chat", Map.of("message", "x".repeat(2001))).status()).isEqualTo(422);
		assertThat(llm.requests()).isEmpty();
	}

	private JsonNode chat(String message, String conversationId) {
		Map<String, Object> body = conversationId == null ? Map.of("message", message)
				: Map.of("message", message, "conversationId", conversationId);
		Response response = user.post("/api/v1/agent/chat", body);
		assertThat(response.status()).as(response.body()).isEqualTo(200);
		return response.json();
	}

	private String monthRange() {
		return "{\"startDate\":\"" + month.atDay(1) + "\",\"endDate\":\"" + month.atEndOfMonth() + "\"}";
	}

	private LlmReply call(String tool, String arguments) {
		return new LlmReply(null, List.of(new ToolCall("call_" + callIds.incrementAndGet(), tool, arguments)), 100,
				20, "scripted-model");
	}

	private static LlmReply text(String answer) {
		return new LlmReply(answer, List.of(), 100, 30, "scripted-model");
	}

	private JsonNode lastToolResult(LlmRequest request) {
		return jsonMapper.readTree(((LlmMessage.ToolResult) request.messages().getLast()).json());
	}

}
