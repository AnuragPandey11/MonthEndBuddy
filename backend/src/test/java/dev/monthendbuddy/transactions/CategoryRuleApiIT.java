package dev.monthendbuddy.transactions;

import static dev.monthendbuddy.transactions.TransactionFixtures.DINING;
import static dev.monthendbuddy.transactions.TransactionFixtures.GROCERIES;
import static dev.monthendbuddy.transactions.TransactionFixtures.SALARY;
import static dev.monthendbuddy.transactions.TransactionFixtures.today;
import static dev.monthendbuddy.transactions.TransactionFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;

class CategoryRuleApiIT extends IntegrationTest {

	private ApiClient as;

	@BeforeEach
	void newUser() {
		as = api().bearer(api().register().accessToken());
	}

	private JsonNode rule(String matchType, String pattern, String categoryId, int priority) {
		Response response = as.post("/api/v1/category-rules",
				Map.of("matchType", matchType, "pattern", pattern, "categoryId", categoryId, "priority", priority));
		assertThat(response.status()).as(response.body()).isEqualTo(201);
		return response.json();
	}

	private JsonNode create(String type, String description, String merchant, String categoryId) {
		return as.post("/api/v1/transactions", transaction(type, "100", today(), description, merchant, categoryId))
			.json();
	}

	@Test
	void rulesCategoriseNewTransactionsWithoutACategory() {
		rule("CONTAINS", "swiggy", DINING, 100);
		JsonNode tx = create("EXPENSE", "Late dinner", "Swiggy", null);
		assertThat(tx.get("category").get("name").asString()).isEqualTo("Dining");
		assertThat(tx.get("categorySource").asString()).isEqualTo("RULE");
	}

	@Test
	void theUsersOwnChoiceAlwaysWins() {
		rule("CONTAINS", "swiggy", DINING, 100);
		JsonNode tx = create("EXPENSE", "Groceries via Swiggy Instamart", "Swiggy", GROCERIES);
		assertThat(tx.get("category").get("name").asString()).isEqualTo("Groceries");
		assertThat(tx.get("categorySource").asString()).isEqualTo("USER");
	}

	@Test
	void lowerPriorityNumberWinsAndKindMustMatch() {
		rule("CONTAINS", "acme", GROCERIES, 200);
		rule("CONTAINS", "acme", DINING, 50);
		rule("CONTAINS", "acme", SALARY, 10);
		assertThat(create("EXPENSE", "Acme canteen", null, null).get("category").get("name").asString())
			.isEqualTo("Dining");
		assertThat(create("INCOME", "Acme payroll", null, null).get("category").get("name").asString())
			.isEqualTo("Salary");
		assertThat(create("EXPENSE", "Something else", null, null).get("categorySource").asString())
			.isEqualTo("NONE");
	}

	@Test
	void rulesCanBeListedUpdatedAndDeleted() {
		JsonNode created = rule("STARTS_WITH", "UPI/BESCOM", DINING, 100);
		String id = created.get("id").asString();
		assertThat(created.get("pattern").asString()).isEqualTo("upi/bescom");

		Response updated = as.put("/api/v1/category-rules/" + id,
				Map.of("matchType", "CONTAINS", "pattern", "bescom", "categoryId", GROCERIES, "priority", 5, "version",
						0));
		assertThat(updated.status()).isEqualTo(200);
		assertThat(updated.json().get("category").get("name").asString()).isEqualTo("Groceries");
		assertThat(as.put("/api/v1/category-rules/" + id, Map.of("matchType", "CONTAINS", "pattern", "x",
				"categoryId", GROCERIES, "version", 0)).status()).isEqualTo(409);

		assertThat(as.get("/api/v1/category-rules").json()).hasSize(1);
		assertThat(as.delete("/api/v1/category-rules/" + id).status()).isEqualTo(204);
		assertThat(as.get("/api/v1/category-rules").json()).isEmpty();
	}

	@Test
	void aRuleMustPointAtAUsableCategory() {
		String othersCategory = api().bearer(api().register().accessToken())
			.post("/api/v1/categories", Map.of("name", "Theirs", "kind", "EXPENSE"))
			.json()
			.get("id")
			.asString();
		Response response = as.post("/api/v1/category-rules",
				Map.of("matchType", "CONTAINS", "pattern", "x", "categoryId", othersCategory));
		assertThat(response.status()).isEqualTo(422);
		assertThat(response.json().get("errors").get(0).get("field").asString()).isEqualTo("categoryId");
	}

}
