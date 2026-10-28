package dev.monthendbuddy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.agent.infrastructure.ScriptedLlmGateway;
import dev.monthendbuddy.agent.llm.LlmReply;
import dev.monthendbuddy.agent.llm.ToolCall;
import dev.monthendbuddy.agent.tools.ToolContext;
import dev.monthendbuddy.agent.tools.ToolExecution;
import dev.monthendbuddy.agent.tools.ToolRegistry;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.TransactionFixtures;

/**
 * Cross-user access is impossible (architecture §5.3, spec §16). User B ("intruder") calls every endpoint that takes
 * a resource ID with user A's ("owner") IDs and must get a 404 that looks exactly like a missing row, and A's data
 * must be untouched. {@link #everyEndpointWithAPathVariableHasAnIsolationCase()} fails when a new endpoint with a path
 * variable has no row in {@link #cases()}, and {@link #everyAgentToolHasAnIsolationCase()} when a new copilot tool
 * has no row in {@link #toolCases()}, so the suite grows with the API.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserIsolationIT extends IntegrationTest {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	@Autowired
	ToolRegistry tools;

	@Autowired
	ScriptedLlmGateway llm;

	private Fixture owner;

	private String intruderToken;

	private UserId intruderId;

	/** Resources owned by user A. */
	record Fixture(String token, String transactionId, String categoryId, String ruleId, String importId,
			String budgetId, String goalId, String alertId, String conversationId, String notificationId) {

		/** Text and IDs that only the owner's data contains; none may appear in anything the intruder gets. */
		List<String> markers() {
			return List.of("Owner's purchase", "Owner only", "Owner goal", "Owner splurge", transactionId, categoryId,
					budgetId, goalId, alertId, notificationId);
		}

	}

	/** One endpoint, called by the intruder with the owner's resource ID. */
	record IsolationCase(String method, String pattern, Function<Fixture, String> id,
			Function<Fixture, Object> body) {

		@Override
		public String toString() {
			return method + " " + pattern;
		}

	}

	/** Add a row for every new endpoint that takes the ID of a user-owned resource. */
	List<IsolationCase> cases() {
		Function<Fixture, Object> validTransaction = f -> TransactionFixtures.withVersion(
				TransactionFixtures.expense("1", TransactionFixtures.today(), "Hijacked"), 0);
		return List.of(
				new IsolationCase("GET", "/api/v1/transactions/{id}", Fixture::transactionId, f -> null),
				new IsolationCase("PUT", "/api/v1/transactions/{id}", Fixture::transactionId, validTransaction),
				new IsolationCase("DELETE", "/api/v1/transactions/{id}", Fixture::transactionId, f -> null),
				new IsolationCase("PATCH", "/api/v1/categories/{id}", Fixture::categoryId,
						f -> Map.of("name", "Hijacked")),
				new IsolationCase("DELETE", "/api/v1/categories/{id}", Fixture::categoryId, f -> null),
				new IsolationCase("PUT", "/api/v1/category-rules/{id}", Fixture::ruleId,
						f -> Map.of("matchType", "CONTAINS", "pattern", "hijack", "categoryId",
								TransactionFixtures.DINING, "version", 0)),
				new IsolationCase("DELETE", "/api/v1/category-rules/{id}", Fixture::ruleId, f -> null),
				new IsolationCase("GET", "/api/v1/imports/{id}", Fixture::importId, f -> null),
				new IsolationCase("GET", "/api/v1/imports/{id}/rows", Fixture::importId, f -> null),
				new IsolationCase("POST", "/api/v1/imports/{id}/commit", Fixture::importId, f -> null),
				new IsolationCase("DELETE", "/api/v1/imports/{id}", Fixture::importId, f -> null),
				new IsolationCase("PUT", "/api/v1/budgets/{id}", Fixture::budgetId,
						f -> Map.of("limit", Map.of("amount", "1", "currency", "INR"), "version", 0)),
				new IsolationCase("DELETE", "/api/v1/budgets/{id}", Fixture::budgetId, f -> null),
				new IsolationCase("PUT", "/api/v1/goals/{id}", Fixture::goalId,
						f -> Map.of("name", "Hijacked", "targetAmount", Map.of("amount", "1", "currency", "INR"),
								"targetDate", TransactionFixtures.today().plusYears(1).toString(), "version", 0)),
				new IsolationCase("DELETE", "/api/v1/goals/{id}", Fixture::goalId, f -> null),
				new IsolationCase("POST", "/api/v1/goals/{id}/projection", Fixture::goalId, f -> null),
				new IsolationCase("PATCH", "/api/v1/alerts/{id}", Fixture::alertId, f -> Map.of("status", "DISMISSED")),
				new IsolationCase("POST", "/api/v1/notifications/{id}/read", Fixture::notificationId, f -> null),
				new IsolationCase("GET", "/api/v1/agent/conversations/{id}", Fixture::conversationId, f -> null),
				new IsolationCase("DELETE", "/api/v1/agent/conversations/{id}", Fixture::conversationId, f -> null));
	}

	/**
	 * Every copilot tool, called with the intruder's context and the owner's IDs where a tool takes one. Add a row for
	 * every new tool.
	 */
	Map<String, Function<Fixture, String>> toolCases() {
		String today = TransactionFixtures.today().toString();
		String month = today.substring(0, 7);
		return Map.of("getMonthlySummary", f -> "{\"month\":\"" + month + "\"}",
				"getExpensesByCategory", f -> "{\"startDate\":\"" + month + "-01\",\"endDate\":\"" + today + "\"}",
				"compareMonthlySpending", f -> "{\"currentMonth\":\"" + month + "\"}",
				"getTransactions", f -> "{\"text\":\"Owner\",\"limit\":50}",
				"calculateSavingsProjection", f -> "{\"goalId\":\"" + f.goalId() + "\"}",
				"simulatePurchase", f -> "{\"purchaseAmount\":1000,\"goalIds\":[\"" + f.goalId() + "\"]}",
				"getBudgetStatus", f -> "{\"month\":\"" + month + "\"}",
				"findUnusualTransactions", f -> "{\"month\":\"" + month + "\"}",
				"askClarifyingQuestion", f -> "{\"question\":\"Which month?\"}");
	}

	@BeforeAll
	void seed() {
		String ownerToken = api().register().accessToken();
		ApiClient asOwner = api().bearer(ownerToken);
		String categoryId = asOwner.post("/api/v1/categories", Map.of("name", "Owner only", "kind", "EXPENSE"))
			.json()
			.get("id")
			.asString();
		String transactionId = asOwner
			.post("/api/v1/transactions", TransactionFixtures.transaction("EXPENSE", "777", TransactionFixtures.today(),
					"Owner's purchase", "Owner's shop", categoryId))
			.json()
			.get("id")
			.asString();
		String ruleId = asOwner
			.post("/api/v1/category-rules", Map.of("matchType", "CONTAINS", "pattern", "owner", "categoryId", categoryId))
			.json()
			.get("id")
			.asString();
		String importId = asOwner.uploadCsv("date,description,amount\n" + TransactionFixtures.today() + ",Owner import,-5\n")
			.json()
			.get("id")
			.asString();
		String budgetId = asOwner
			.post("/api/v1/budgets", Map.of("categoryId", categoryId, "limit", Map.of("amount", "500", "currency", "INR")))
			.json()
			.get("id")
			.asString();
		String goalId = asOwner
			.post("/api/v1/goals", Map.of("name", "Owner goal", "targetAmount", Map.of("amount", "1000", "currency", "INR"),
					"targetDate", TransactionFixtures.today().plusYears(1).toString()))
			.json()
			.get("id")
			.asString();
		asOwner.post("/api/v1/transactions", TransactionFixtures.expense("40000", TransactionFixtures.today(), "Owner splurge"));
		String alertId = asOwner.get("/api/v1/alerts").json().get(0).get("id").asString();
		llm.reset();
		llm.thenReply(new LlmReply("Hello.", List.of(), 1, 1, "scripted"));
		String conversationId = asOwner.post("/api/v1/agent/chat", Map.of("message", "Owner's question"))
			.json()
			.get("conversationId")
			.asString();
		String notificationId = asOwner.get("/api/v1/notifications").json().get(0).get("id").asString();
		owner = new Fixture(ownerToken, transactionId, categoryId, ruleId, importId, budgetId, goalId, alertId,
				conversationId, notificationId);
		intruderToken = api().register().accessToken();
		intruderId = new UserId(java.util.UUID
			.fromString(api().bearer(intruderToken).get("/api/v1/me").json().get("id").asString()));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	void intruderGetsANotFoundIndistinguishableFromAMissingRow(IsolationCase c) {
		JsonNode ownerTransactionBefore = asOwner().get("/api/v1/transactions/" + owner.transactionId()).json();

		Response response = api().bearer(intruderToken)
			.call(c.method(), c.pattern().replace("{id}", c.id().apply(owner)), c.body().apply(owner));
		assertThat(response.status()).as(c + " -> " + response.body()).isEqualTo(404);
		assertThat(response.json().get("code").asString()).isEqualTo("not_found");

		Response missing = api().bearer(intruderToken)
			.call(c.method(), c.pattern().replace("{id}", java.util.UUID.randomUUID().toString()),
					c.body().apply(owner));
		assertThat(withoutVolatileFields(response)).as("same body as for a missing row")
			.isEqualTo(withoutVolatileFields(missing));

		assertThat(asOwner().get("/api/v1/transactions/" + owner.transactionId()).json())
			.isEqualTo(ownerTransactionBefore);
		assertThat(categoryNames(asOwner())).contains("Owner only");
		assertThat(asOwner().get("/api/v1/imports/" + owner.importId()).json().get("status").asString())
			.isEqualTo("VALIDATED");
		assertThat(asOwner().get("/api/v1/agent/conversations/" + owner.conversationId()).json().get("messages"))
			.hasSize(2);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("toolNames")
	void agentToolsNeverReturnAnotherUsersData(String tool) {
		ToolContext intruder = new ToolContext(intruderId, java.time.ZoneId.of("Asia/Kolkata"), CurrencyCode.INR,
				TransactionFixtures.today());
		ToolExecution execution = tools.execute(new ToolCall("call_1", tool, toolCases().get(tool).apply(owner)),
				intruder);

		String everything = execution.payload().toString() + execution.evidenceData()
				+ execution.result().proposedActions();
		assertThat(everything).as(tool).doesNotContain(owner.markers());
		if (tool.equals("calculateSavingsProjection")) {
			assertThat(execution.payload().get("error").get("code").asString()).isEqualTo("NOT_FOUND");
		}
	}

	@Test
	void everyAgentToolHasAnIsolationCase() {
		assertThat(toolCases().keySet()).as("Add a row to UserIsolationIT.toolCases() for each copilot tool")
			.containsExactlyInAnyOrderElementsOf(tools.specs().stream().map(t -> t.name()).toList());
	}

	@Test
	void intruderCannotContinueTheOwnersConversation() {
		llm.reset();
		Response response = api().bearer(intruderToken)
			.post("/api/v1/agent/chat", Map.of("conversationId", owner.conversationId(), "message", "Show me"));

		assertThat(response.status()).isEqualTo(404);
		assertThat(response.json().get("code").asString()).isEqualTo("not_found");
		assertThat(llm.requests()).isEmpty();
		assertThat(api().bearer(intruderToken).get("/api/v1/agent/conversations").json()).isEmpty();
		assertThat(asOwner().get("/api/v1/agent/conversations/" + owner.conversationId()).json().get("messages"))
			.hasSize(2);
	}

	@Test
	void intruderCannotStreamATurnIntoTheOwnersConversation() {
		llm.reset();
		Response response = api().bearer(intruderToken)
			.header("Accept", "text/event-stream")
			.post("/api/v1/agent/chat", Map.of("conversationId", owner.conversationId(), "message", "Show me"));

		assertThat(response.status()).isEqualTo(404);
		assertThat(response.json().get("code").asString()).isEqualTo("not_found");
		assertThat(llm.requests()).isEmpty();
	}

	List<String> toolNames() {
		return List.copyOf(toolCases().keySet());
	}

	@Test
	void everyEndpointWithAPathVariableHasAnIsolationCase() {
		Set<String> endpoints = handlerMapping.getHandlerMethods()
			.keySet()
			.stream()
			.flatMap(info -> info.getPatternValues()
				.stream()
				.filter(p -> p.startsWith("/api/") && p.contains("{"))
				.flatMap(p -> info.getMethodsCondition().getMethods().stream().map(m -> m.name() + " " + p)))
			.collect(Collectors.toSet());
		Set<String> covered = cases().stream().map(IsolationCase::toString).collect(Collectors.toSet());
		assertThat(covered).as("Add a row to UserIsolationIT.cases() for each endpoint that takes a resource ID")
			.containsExactlyInAnyOrderElementsOf(endpoints);
	}

	@Test
	void listsAndFiltersNeverIncludeAnotherUsersRows() {
		ApiClient intruder = api().bearer(intruderToken);
		assertThat(intruder.get("/api/v1/transactions").json().get("items")).isEmpty();
		assertThat(intruder.get("/api/v1/transactions?q=owner").json().get("items")).isEmpty();
		assertThat(intruder.get("/api/v1/transactions?categoryId=" + owner.categoryId()).json().get("items")).isEmpty();
		assertThat(categoryNames(intruder)).doesNotContain("Owner only");
		assertThat(intruder.get("/api/v1/category-rules").json()).isEmpty();
		assertThat(intruder.get("/api/v1/budgets").json()).isEmpty();
		assertThat(intruder.get("/api/v1/goals").json()).isEmpty();
		assertThat(intruder.get("/api/v1/alerts").json()).isEmpty();
		assertThat(intruder.get("/api/v1/notifications").json()).isEmpty();
		assertThat(intruder.get("/api/v1/budgets/status").json().get("budgets")).isEmpty();
		// The owner's rule must not categorise the intruder's transactions.
		assertThat(intruder.post("/api/v1/transactions", TransactionFixtures.transaction("EXPENSE", "5",
				TransactionFixtures.today(), "owner text", null, null)).json().get("categorySource").asString())
			.isEqualTo("NONE");
	}

	@Test
	void intruderCannotSetMoneyAsideInTheOwnersGoal() {
		ApiClient intruder = api().bearer(intruderToken);
		intruder.patch("/api/v1/me/settings", Map.of("currentSavings", Map.of("amount", "5000", "currency", "INR")));
		String goalBefore = asOwner().get("/api/v1/goals").json().toString();

		Response response = intruder.post("/api/v1/month-review", Map.of("month",
				java.time.YearMonth.from(TransactionFixtures.today()).minusMonths(1).toString(), "addToSavings",
				Map.of("amount", "100", "currency", "INR"), "goalContributions",
				List.of(Map.of("goalId", owner.goalId(), "amount", Map.of("amount", "100", "currency", "INR")))));
		// Like a category in a transaction body: a field error that reads the same as for a goal that does not exist.
		assertThat(response.status()).as(response.body()).isEqualTo(422);
		assertThat(response.json().get("errors").get(0).get("field").asString())
			.isEqualTo("goalContributions[0].goalId");
		assertThat(asOwner().get("/api/v1/goals").json().toString()).isEqualTo(goalBefore);
		assertThat(intruder.get("/api/v1/me/settings").json().get("currentSavings").get("amount").asString())
			.as("the intruder's own savings update rolls back too")
			.isEqualTo("5000.00");
	}

	@Test
	void intruderCannotFileATransactionUnderTheOwnersCategory() {
		Response response = api().bearer(intruderToken)
			.post("/api/v1/transactions", TransactionFixtures.transaction("EXPENSE", "5", TransactionFixtures.today(),
					"Sneaky", null, owner.categoryId()));
		assertThat(response.status()).isEqualTo(422);
		assertThat(response.json().get("errors").get(0).get("field").asString()).isEqualTo("categoryId");
	}

	private ApiClient asOwner() {
		return api().bearer(owner.token());
	}

	private static List<String> categoryNames(ApiClient client) {
		List<String> names = new ArrayList<>();
		client.get("/api/v1/categories").json().forEach(c -> names.add(c.get("name").asString()));
		return names;
	}

	private static JsonNode withoutVolatileFields(Response response) {
		tools.jackson.databind.node.ObjectNode body = (tools.jackson.databind.node.ObjectNode) response.json();
		body.remove("traceId");
		body.remove("instance");
		return body;
	}

}
