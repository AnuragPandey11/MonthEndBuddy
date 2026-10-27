package dev.monthendbuddy.transactions;

import static dev.monthendbuddy.transactions.TransactionFixtures.DINING;
import static dev.monthendbuddy.transactions.TransactionFixtures.GROCERIES;
import static dev.monthendbuddy.transactions.TransactionFixtures.SALARY;
import static dev.monthendbuddy.transactions.TransactionFixtures.expense;
import static dev.monthendbuddy.transactions.TransactionFixtures.today;
import static dev.monthendbuddy.transactions.TransactionFixtures.transaction;
import static dev.monthendbuddy.transactions.TransactionFixtures.withVersion;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;

class TransactionApiIT extends IntegrationTest {

	private String token;

	@BeforeEach
	void newUser() {
		token = api().register().accessToken();
	}

	private ApiClient as() {
		return api().bearer(token);
	}

	private JsonNode create(Map<String, Object> body) {
		Response response = as().post("/api/v1/transactions", body);
		assertThat(response.status()).as(response.body()).isEqualTo(201);
		return response.json();
	}

	@Test
	void createGetUpdateDeleteLifecycle() {
		LocalDate date = today().minusDays(3);
		Response created = as().post("/api/v1/transactions",
				transaction("EXPENSE", "1250.5", date, "Dinner", "Swiggy", DINING));
		assertThat(created.status()).isEqualTo(201);
		JsonNode tx = created.json();
		String id = tx.get("id").asString();
		assertThat(created.header("Location")).hasValue("/api/v1/transactions/" + id);
		assertThat(tx.get("amount").get("amount").asString()).isEqualTo("1250.50");
		assertThat(tx.get("amount").get("currency").asString()).isEqualTo("INR");
		assertThat(tx.get("transactionDate").asString()).isEqualTo(date.toString());
		assertThat(tx.get("category").get("name").asString()).isEqualTo("Dining");
		assertThat(tx.get("categorySource").asString()).isEqualTo("USER");
		assertThat(tx.get("source").asString()).isEqualTo("MANUAL");
		assertThat(tx.get("version").asLong()).isZero();

		assertThat(as().get("/api/v1/transactions/" + id).json()).isEqualTo(tx);

		Response updated = as().put("/api/v1/transactions/" + id,
				withVersion(transaction("EXPENSE", "980", date, "Dinner (split)", "Swiggy", GROCERIES), 0));
		assertThat(updated.status()).isEqualTo(200);
		assertThat(updated.json().get("version").asLong()).isEqualTo(1);
		assertThat(updated.json().get("category").get("name").asString()).isEqualTo("Groceries");

		assertThat(as().delete("/api/v1/transactions/" + id).status()).isEqualTo(204);
		assertThat(as().get("/api/v1/transactions/" + id).status()).isEqualTo(404);
		assertThat(as().delete("/api/v1/transactions/" + id).status()).isEqualTo(404);
	}

	@Test
	void freeTextIsSanitizedAndUncategorisedIsAllowed() {
		Map<String, Object> body = transaction("EXPENSE", "99", today(), "  Chai\u0000 \tbreak\n", " Tapri ", null);
		body.put("notes", "line one\r\nline two\u0007");
		JsonNode tx = create(body);
		assertThat(tx.get("description").asString()).isEqualTo("Chai break");
		assertThat(tx.get("merchant").asString()).isEqualTo("Tapri");
		assertThat(tx.get("notes").asString()).isEqualTo("line one\nline two");
		assertThat(tx.get("category").isNull()).isTrue();
		assertThat(tx.get("categorySource").asString()).isEqualTo("NONE");
	}

	@Test
	void invalidInputIsRejectedWith422AndTheOffendingField() {
		Map<String, Object> usd = expense("10", today(), "Coffee");
		usd.put("amount", Map.of("amount", "10", "currency", "USD"));
		assertProblem(as().post("/api/v1/transactions", usd), "unsupported_currency", "amount.currency");
		assertProblem(as().post("/api/v1/transactions", expense("10", today().plusDays(1), "Coffee")),
				"validation_failed", "transactionDate");
		assertProblem(as().post("/api/v1/transactions", transaction("EXPENSE", "10", today(), "x", null, SALARY)),
				"validation_failed", "categoryId");
		assertProblem(as().post("/api/v1/transactions", expense("0", today(), "Coffee")), "validation_failed",
				"amount");
		assertProblem(as().post("/api/v1/transactions", expense("10.005", today(), "Coffee")), "validation_failed",
				"amount");
		assertProblem(as().post("/api/v1/transactions", expense("10", today(), "  ")), "validation_failed",
				"description");
	}

	@Test
	void staleVersionIsAConflict() {
		String id = create(expense("100", today(), "Taxi")).get("id").asString();
		assertThat(as().put("/api/v1/transactions/" + id, withVersion(expense("120", today(), "Taxi"), 0)).status())
			.isEqualTo(200);
		Response stale = as().put("/api/v1/transactions/" + id, withVersion(expense("130", today(), "Taxi"), 0));
		assertThat(stale.status()).isEqualTo(409);
		assertThat(stale.json().get("code").asString()).isEqualTo("version_conflict");
	}

	@Test
	void anArchivedCategoryCannotBeChosenButExistingTransactionsKeepIt() {
		String categoryId = as().post("/api/v1/categories", Map.of("name", "Pets", "kind", "EXPENSE"))
			.json()
			.get("id")
			.asString();
		JsonNode tx = create(transaction("EXPENSE", "500", today(), "Vet", null, categoryId));
		assertThat(as().delete("/api/v1/categories/" + categoryId).status()).isEqualTo(204);

		assertProblem(as().post("/api/v1/transactions", transaction("EXPENSE", "50", today(), "Food", null, categoryId)),
				"validation_failed", "categoryId");
		Response edit = as().put("/api/v1/transactions/" + tx.get("id").asString(),
				withVersion(transaction("EXPENSE", "550", today(), "Vet visit", null, categoryId), 0));
		assertThat(edit.status()).as(edit.body()).isEqualTo(200);
		assertThat(edit.json().get("category").get("name").asString()).isEqualTo("Pets");
		assertThat(edit.json().get("categorySource").asString()).isEqualTo("USER");
	}

	@Test
	void keysetPaginationIsStableAcrossRowsOnTheSameDate() {
		LocalDate busy = today().minusDays(10);
		for (int i = 0; i < 7; i++) {
			create(expense(String.valueOf(10 + i), busy, "Same-day #" + i));
		}
		create(expense("1", today(), "Newest"));
		create(expense("2", busy.minusDays(1), "Oldest"));

		List<JsonNode> seen = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			Response page = as().get("/api/v1/transactions?limit=3" + (cursor == null ? "" : "&cursor=" + cursor));
			assertThat(page.status()).isEqualTo(200);
			page.json().get("items").forEach(seen::add);
			JsonNode next = page.json().get("nextCursor");
			cursor = next.isNull() ? null : next.asString();
			pages++;
		}
		while (cursor != null);

		assertThat(pages).isEqualTo(3);
		assertThat(seen).hasSize(9);
		assertThat(seen.stream().map(n -> n.get("id").asString()).distinct()).hasSize(9);
		assertThat(seen.get(0).get("description").asString()).isEqualTo("Newest");
		assertThat(seen.get(8).get("description").asString()).isEqualTo("Oldest");
		assertThat(seen).isSortedAccordingTo(Comparator
			.comparing((JsonNode n) -> n.get("transactionDate").asString())
			.thenComparing(n -> n.get("id").asString())
			.reversed());
	}

	@Test
	void filtersCombine() {
		LocalDate d = today().minusDays(20);
		create(transaction("EXPENSE", "450", d, "Lunch", "Swiggy", DINING));
		create(transaction("EXPENSE", "1200", d.plusDays(1), "Weekly shop", "BigBasket", GROCERIES));
		create(transaction("INCOME", "95000", d.plusDays(2), "October salary", "Acme Corp", SALARY));
		create(transaction("EXPENSE", "75", d.plusDays(3), "100% juice", null, null));

		assertThat(descriptions("?q=SWIG")).containsExactly("Lunch");
		assertThat(descriptions("?q=100%25")).containsExactly("100% juice");
		assertThat(descriptions("?q=_")).isEmpty();
		assertThat(descriptions("?type=INCOME")).containsExactly("October salary");
		assertThat(descriptions("?categoryId=" + GROCERIES)).containsExactly("Weekly shop");
		assertThat(descriptions("?minAmount=400&maxAmount=1200")).containsExactly("Weekly shop", "Lunch");
		assertThat(descriptions("?from=" + d.plusDays(1) + "&to=" + d.plusDays(2)))
			.containsExactly("October salary", "Weekly shop");
		assertProblem(as().get("/api/v1/transactions?from=" + d.plusDays(1) + "&to=" + d), "validation_failed", "from");
	}

	@Test
	void pageSizeAndCursorAreValidated() {
		assertProblem(as().get("/api/v1/transactions?limit=0"), "validation_failed", "limit");
		assertProblem(as().get("/api/v1/transactions?limit=101"), "validation_failed", "limit");
		assertProblem(as().get("/api/v1/transactions?cursor=bm90LWEtY3Vyc29y"), "validation_failed", "cursor");
		assertThat(as().get("/api/v1/transactions?type=REFUND").status()).isEqualTo(400);
	}

	private List<String> descriptions(String query) {
		Response response = as().get("/api/v1/transactions" + query);
		assertThat(response.status()).as(response.body()).isEqualTo(200);
		List<String> result = new ArrayList<>();
		response.json().get("items").forEach(n -> result.add(n.get("description").asString()));
		return result;
	}

	private static void assertProblem(Response response, String code, String field) {
		assertThat(response.status()).as(response.body()).isEqualTo(422);
		assertThat(response.json().get("code").asString()).isEqualTo(code);
		List<String> fields = new ArrayList<>();
		response.json().get("errors").forEach(e -> fields.add(e.get("field").asString()));
		assertThat(fields).contains(field);
	}

}
