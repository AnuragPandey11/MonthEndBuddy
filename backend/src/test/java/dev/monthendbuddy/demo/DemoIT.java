package dev.monthendbuddy.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.TransactionFixtures;

/** The shared demo account (architecture §5.2, implementation plan P1.5). */
class DemoIT extends IntegrationTest {

	@Autowired
	DemoDataService demo;

	private String demoToken() {
		Response login = api().post("/api/v1/auth/demo", null);
		assertThat(login.status()).as(login.body()).isEqualTo(200);
		assertThat(login.cookie("meb_refresh")).isPresent();
		return login.json().get("accessToken").asString();
	}

	private List<JsonNode> allTransactions(ApiClient client) {
		List<JsonNode> all = new ArrayList<>();
		String cursor = null;
		do {
			JsonNode page = client.get("/api/v1/transactions?limit=100" + (cursor == null ? "" : "&cursor=" + cursor))
				.json();
			page.get("items").forEach(all::add);
			cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
		}
		while (cursor != null);
		return all;
	}

	@Test
	void demoLoginOpensTheLabelledDemoAccountWithSixMonthsOfData() {
		ApiClient asDemo = api().bearer(demoToken());

		JsonNode me = asDemo.get("/api/v1/me").json();
		assertThat(me.get("demo").asBoolean()).isTrue();
		assertThat(me.get("email").asString()).isEqualTo("demo@monthendbuddy.dev");
		assertThat(asDemo.get("/api/v1/me/settings").json().get("currentSavings").get("amount").asString())
			.isEqualTo("310000.00");

		List<JsonNode> transactions = allTransactions(asDemo);
		assertThat(transactions.size()).isBetween(150, 300);
		assertThat(transactions.stream().map(t -> t.get("transactionDate").asString().substring(0, 7)).distinct())
			.hasSize(6);
		assertThat(transactions).allSatisfy(t -> {
			assertThat(t.get("source").asString()).isEqualTo("DEMO_SEED");
			assertThat(t.get("category").get("name").asString()).isNotBlank();
			assertThat(t.get("transactionDate").asString())
				.isLessThanOrEqualTo(TransactionFixtures.today().toString());
		});
	}

	@Test
	void nightlyResetWipesVisitorChangesAndIsRepeatable() {
		ApiClient asDemo = api().bearer(demoToken());
		asDemo.post("/api/v1/transactions", TransactionFixtures.expense("1", TransactionFixtures.today(), "Visitor"));
		asDemo.post("/api/v1/categories", Map.of("name", "Visitor category", "kind", "EXPENSE"));

		int first = demo.resetDemoUser();
		List<JsonNode> afterReset = allTransactions(asDemo);
		assertThat(afterReset).hasSize(first).noneMatch(t -> t.get("source").asString().equals("MANUAL"));
		List<String> categories = new ArrayList<>();
		asDemo.get("/api/v1/categories").json().forEach(c -> categories.add(c.get("name").asString()));
		assertThat(categories).doesNotContain("Visitor category");

		assertThat(demo.resetDemoUser()).isEqualTo(first);
	}

	@Test
	void theDemoAccountHasBudgetsGoalsAndThePlantedAlerts() {
		ApiClient asDemo = api().bearer(demoToken());
		assertThat(asDemo.get("/api/v1/budgets").json()).extracting(b -> b.get("categoryName").asString())
			.containsExactly("Overall", "Dining", "Groceries", "Shopping");
		assertThat(asDemo.get("/api/v1/goals").json()).extracting(g -> g.get("name").asString())
			.containsExactlyInAnyOrder("Emergency fund", "New laptop");
		List<String> rules = new ArrayList<>();
		asDemo.get("/api/v1/alerts").json().forEach(a -> rules.add(a.get("ruleId").asString() + " " + a.get("subjectLabel").asString()));
		assertThat(rules).contains("LARGE_VS_CATEGORY The Leela Palace", "LARGE_VS_CATEGORY Croma",
				"CATEGORY_SPIKE Travel");
	}

	@Test
	void theDemoEmailCannotBeRegistered() {
		Response response = api().post("/api/v1/auth/register",
				Map.of("email", "DEMO@monthendbuddy.dev", "password", "correct-horse-battery", "displayName", "X"));
		assertThat(response.status()).isEqualTo(409);
		assertThat(response.json().get("code").asString()).isEqualTo("email_taken");
	}

}
