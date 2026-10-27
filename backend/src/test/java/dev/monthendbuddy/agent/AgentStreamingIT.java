package dev.monthendbuddy.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import dev.monthendbuddy.agent.application.AgentTurnLock;
import dev.monthendbuddy.agent.infrastructure.ScriptedLlmGateway;
import dev.monthendbuddy.agent.llm.LlmReply;
import dev.monthendbuddy.agent.llm.ToolCall;
import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.TransactionFixtures;

/** The SSE transport (architecture §10.7): the same turn as the JSON endpoint, streamed as it runs. */
class AgentStreamingIT extends IntegrationTest {

	private static final String SSE = "text/event-stream";

	/** What the SPA sends: the stream, or a problem if the turn is not admitted. */
	private static final String CLIENT_ACCEPT = "text/event-stream, application/problem+json";

	@Autowired
	ScriptedLlmGateway llm;

	@Autowired
	AgentTurnLock lock;

	private String token;

	private ApiClient user;

	private final YearMonth month = YearMonth.from(TransactionFixtures.today());

	@BeforeEach
	void seed() {
		llm.reset();
		token = api().register().accessToken();
		user = api().bearer(token);
		user.post("/api/v1/transactions", TransactionFixtures.transaction("EXPENSE", "1250.50", month.atDay(1),
				"Dinner", "Bistro", TransactionFixtures.DINING));
	}

	@Test
	void toolEventsArriveInOrderAndTheFinalEventEqualsTheJsonResponse() {
		scriptTwoTools();
		Response streamed = stream(Map.of("message", "Where did my money go?"));

		assertThat(streamed.status()).isEqualTo(200);
		assertThat(streamed.header("Content-Type").orElseThrow()).startsWith(SSE);
		assertThat(streamed.header("X-Accel-Buffering")).contains("no");
		List<Event> events = parse(streamed.body());
		assertThat(events).extracting(Event::name)
			.containsExactly("tool_started", "tool_finished", "tool_started", "tool_finished", "final");
		assertThat(events.get(0).data().get("tool").asString()).isEqualTo("getMonthlySummary");
		assertThat(events.get(0).data().get("label").asString()).isNotBlank();
		assertThat(events.get(1).data().get("ok").asBoolean()).isTrue();
		assertThat(events.get(2).data().get("callId").asString()).isEqualTo("call_b");

		llm.reset();
		scriptTwoTools();
		JsonNode json = user.post("/api/v1/agent/chat", Map.of("message", "Where did my money go?")).json();

		JsonNode fin = events.getLast().data();
		assertThat(fin.get("status").asString()).isEqualTo("COMPLETE");
		assertThat(withoutIds(fin)).isEqualTo(withoutIds(json));
	}

	@Test
	void withoutAnSseAcceptHeaderTheEndpointStillAnswersWithJson() {
		llm.thenReply(text("Plain.")).thenReply(text("Plain."));

		Response none = user.post("/api/v1/agent/chat", Map.of("message", "Hi"));
		Response wildcard = api().bearer(token).header("Accept", "*/*").post("/api/v1/agent/chat",
				Map.of("message", "Hi"));

		for (Response response : List.of(none, wildcard)) {
			assertThat(response.status()).isEqualTo(200);
			assertThat(response.header("Content-Type").orElseThrow()).startsWith("application/json");
			assertThat(response.json().get("answer").asString()).isEqualTo("Plain.");
		}
	}

	@Test
	void aProviderFailureIsOneFinalEventNotAnError() {
		llm.thenFail();

		List<Event> events = parse(stream(Map.of("message", "Hello?")).body());

		assertThat(events).extracting(Event::name).containsExactly("final");
		assertThat(events.getFirst().data().get("status").asString()).isEqualTo("PROVIDER_UNAVAILABLE");
	}

	@Test
	void aTurnThatThrowsEndsWithAnErrorEventCarryingTheTraceId() {
		llm.then(request -> {
			throw new IllegalStateException("scripted crash");
		});

		List<Event> events = parse(stream(Map.of("message", "Crash")).body());

		assertThat(events).extracting(Event::name).containsExactly("error");
		assertThat(events.getFirst().data().get("code").asString()).isEqualTo("internal_error");
		assertThat(events.getFirst().data().get("traceId").asString()).isNotBlank();
	}

	@Test
	void admissionFailuresAreProblemResponsesNotStreams() {
		Response invalid = stream(Map.of("message", " "));
		assertThat(invalid.status()).isEqualTo(422);
		assertThat(invalid.header("Content-Type").orElseThrow()).startsWith("application/problem+json");

		llm.thenReply(text("First."));
		String id = user.post("/api/v1/agent/chat", Map.of("message", "Start")).json().get("conversationId").asString();
		assertThat(lock.tryAcquire(UUID.fromString(id), Duration.ofSeconds(30))).isTrue();
		try {
			Response conflict = stream(Map.of("conversationId", id, "message", "Again"));
			assertThat(conflict.status()).isEqualTo(409);
			assertThat(conflict.header("Content-Type").orElseThrow()).startsWith("application/problem+json");
			assertThat(conflict.json().get("code").asString()).isEqualTo("turn_in_progress");
		}
		finally {
			lock.release(UUID.fromString(id));
		}
		assertThat(llm.requests()).hasSize(1);
	}

	@Test
	void theLockIsReleasedWhenAStreamedTurnEnds() {
		llm.thenReply(text("First.")).thenReply(text("Second.")).thenReply(text("Third."));
		String id = user.post("/api/v1/agent/chat", Map.of("message", "Start")).json().get("conversationId").asString();

		assertThat(parse(stream(Map.of("conversationId", id, "message", "Next")).body())).extracting(Event::name)
			.containsExactly("final");
		Response after = stream(Map.of("conversationId", id, "message", "And again"));

		assertThat(after.status()).isEqualTo(200);
		assertThat(parse(after.body()).getLast().data().get("answer").asString()).isEqualTo("Third.");
	}

	/** The P4.1 decision: a disconnect does not cancel the turn, which finishes and is stored. */
	@Test
	void aClientThatDisconnectsMidTurnStillFindsTheAnswerStored() throws Exception {
		CountDownLatch disconnected = new CountDownLatch(1);
		llm.thenReply(call("call_a", "getMonthlySummary", "{}")).then(request -> {
			await(disconnected);
			return text("Finished without you.");
		});

		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + apiPort + "/api/v1/agent/chat"))
			.header("Authorization", "Bearer " + token)
			.header("Accept", SSE)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"Leave early\"}"))
			.build();
		try (HttpClient http = HttpClient.newHttpClient()) {
			HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = response.body()) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
				assertThat(reader.readLine()).isEqualTo("event:tool_started");
			}
		}
		disconnected.countDown();

		JsonNode messages = awaitStoredAnswer();
		assertThat(messages.get(1).get("content").asString()).isEqualTo("Finished without you.");
		assertThat(messages.get(1).get("metadata").get("evidence")).hasSize(1);
	}

	private JsonNode awaitStoredAnswer() throws InterruptedException {
		Instant until = Instant.now().plusSeconds(10);
		while (Instant.now().isBefore(until)) {
			JsonNode list = user.get("/api/v1/agent/conversations").json();
			if (!list.isEmpty()) {
				JsonNode messages = user.get("/api/v1/agent/conversations/" + list.get(0).get("id").asString())
					.json()
					.get("messages");
				if (messages.size() == 2) {
					return messages;
				}
			}
			Thread.sleep(100);
		}
		throw new AssertionError("The turn was not stored after the client disconnected");
	}

	private void scriptTwoTools() {
		llm.thenReply(call("call_a", "getMonthlySummary", "{}"))
			.thenReply(call("call_b", "getExpensesByCategory",
					"{\"startDate\":\"" + month.atDay(1) + "\",\"endDate\":\"" + month.atEndOfMonth() + "\"}"))
			.thenReply(text("You spent ₹1250.50 this month."));
	}

	private Response stream(Map<String, Object> body) {
		return api().bearer(token).header("Accept", CLIENT_ACCEPT).post("/api/v1/agent/chat", body);
	}

	private JsonNode withoutIds(JsonNode response) {
		ObjectNode copy = (ObjectNode) response.deepCopy();
		copy.remove("conversationId");
		copy.remove("messageId");
		return copy;
	}

	record Event(String name, JsonNode data) {
	}

	/** Spring writes {@code event:} and {@code data:} lines with no space after the colon. */
	private List<Event> parse(String body) {
		List<Event> events = new ArrayList<>();
		for (String block : body.split("\n\n")) {
			String name = null;
			StringBuilder data = new StringBuilder();
			for (String line : block.split("\n")) {
				if (line.startsWith("event:")) {
					name = line.substring("event:".length());
				}
				else if (line.startsWith("data:")) {
					data.append(line.substring("data:".length()));
				}
			}
			if (name != null) {
				events.add(new Event(name, jsonMapper.readTree(data.toString())));
			}
		}
		return events;
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static LlmReply call(String id, String tool, String arguments) {
		return new LlmReply(null, List.of(new ToolCall(id, tool, arguments)), 100, 20, "scripted-model");
	}

	private static LlmReply text(String answer) {
		return new LlmReply(answer, List.of(), 100, 30, "scripted-model");
	}

}
