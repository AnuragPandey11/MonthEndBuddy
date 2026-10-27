package dev.monthendbuddy.analytics;

import static dev.monthendbuddy.transactions.TransactionFixtures.SALARY;
import static dev.monthendbuddy.transactions.TransactionFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.TransactionFixtures;

/**
 * The month-end review (architecture §9.6): last month's kept money is offered once, and confirming adds to Savings
 * today and to goals in one transaction that cannot run twice.
 */
class MonthReviewApiIT extends IntegrationTest {

	private final YearMonth lastMonth = YearMonth.from(TransactionFixtures.today()).minusMonths(1);

	private ApiClient as;

	@BeforeEach
	void user() {
		as = api().bearer(api().register().accessToken());
	}

	private static Map<String, Object> money(String amount) {
		return Map.of("amount", amount, "currency", "INR");
	}

	private void lastMonthKept() {
		as.post("/api/v1/transactions", transaction("INCOME", "95000", lastMonth.atDay(1), "Salary", null, SALARY));
		as.post("/api/v1/transactions",
				TransactionFixtures.expense("71250.50", lastMonth.atDay(5), "Rent and groceries"));
	}

	private String goal(String name, String target, String saved) {
		return as
			.post("/api/v1/goals",
					Map.of("name", name, "targetAmount", money(target), "currentAmount", money(saved), "targetDate",
							lastMonth.plusMonths(6).atDay(1).toString()))
			.json()
			.get("id")
			.asString();
	}

	private JsonNode review() {
		return as.get("/api/v1/month-review").json();
	}

	private String savings() {
		return as.get("/api/v1/me/settings").json().get("currentSavings").get("amount").asString();
	}

	private String goalSaved(String id) {
		for (JsonNode g : as.get("/api/v1/goals").json()) {
			if (g.get("id").asString().equals(id)) {
				return g.get("current").get("amount").asString();
			}
		}
		throw new AssertionError("no goal " + id);
	}

	private static String field(Response response) {
		return response.json().get("errors").get(0).get("field").asString();
	}

	@Test
	void aMonthThatKeptMoneyIsDueAndConfirmingAddsToSavingsAndGoalsOnce() {
		as.patch("/api/v1/me/settings", Map.of("currentSavings", money("80000")));
		String phone = goal("Phone", "80000", "20000");
		lastMonthKept();

		JsonNode due = review();
		assertThat(due.get("month").asString()).isEqualTo(lastMonth.toString());
		assertThat(due.get("status").asString()).isEqualTo("DUE");
		assertThat(due.get("kept").get("amount").asString()).isEqualTo("23749.50");
		assertThat(due.get("currentSavings").get("amount").asString()).isEqualTo("80000.00");
		// Savings were set today, after the month ended: they may already include what it kept.
		assertThat(due.get("savingsUpdatedSinceMonthEnd").asBoolean()).isTrue();

		Response done = as.post("/api/v1/month-review", Map.of("month", lastMonth.toString(), "addToSavings",
				money("23749.50"), "goalContributions", List.of(Map.of("goalId", phone, "amount", money("10000")))));
		assertThat(done.status()).as(done.body()).isEqualTo(200);
		assertThat(done.json().get("status").asString()).isEqualTo("DONE");
		assertThat(done.json().get("addedToSavings").get("amount").asString()).isEqualTo("23749.50");
		assertThat(done.json().get("setAsideForGoals").get("amount").asString()).isEqualTo("10000.00");
		assertThat(savings()).isEqualTo("103749.50");
		assertThat(goalSaved(phone)).isEqualTo("30000.00");

		Response again = as.post("/api/v1/month-review",
				Map.of("month", lastMonth.toString(), "addToSavings", money("23749.50")));
		assertThat(again.status()).isEqualTo(409);
		assertThat(again.json().get("code").asString()).isEqualTo("month_already_reviewed");
		assertThat(savings()).as("nothing added twice").isEqualTo("103749.50");
		assertThat(review().get("status").asString()).isEqualTo("DONE");
	}

	@Test
	void aMonthWithoutDataOrWithoutSurplusHasNothingToReview() {
		assertThat(review().get("status").asString()).isEqualTo("NOTHING_KEPT");
		assertThat(review().get("savingsUpdatedSinceMonthEnd").asBoolean()).as("savings never set").isFalse();
		assertThat(review().get("transactionCount").asLong()).isZero();

		as.post("/api/v1/transactions", TransactionFixtures.expense("500", lastMonth.atDay(3), "Only spending"));
		assertThat(review().get("status").asString()).isEqualTo("NOTHING_KEPT");
	}

	@Test
	void confirmingWithNothingRecordsTheMonthAsSkipped() {
		lastMonthKept();
		Response skipped = as.post("/api/v1/month-review", Map.of("month", lastMonth.toString()));
		assertThat(skipped.status()).as(skipped.body()).isEqualTo(200);
		assertThat(skipped.json().get("status").asString()).isEqualTo("SKIPPED");
		assertThat(review().get("status").asString()).isEqualTo("SKIPPED");
	}

	@Test
	void onlyLastMonthCanBeReviewedAndUnknownSavingsAreNeverInvented() {
		lastMonthKept();
		Response wrongMonth = as.post("/api/v1/month-review", Map.of("month", lastMonth.minusMonths(1).toString()));
		assertThat(wrongMonth.status()).isEqualTo(422);
		assertThat(field(wrongMonth)).isEqualTo("month");

		Response unknownSavings = as.post("/api/v1/month-review",
				Map.of("month", lastMonth.toString(), "addToSavings", money("1000")));
		assertThat(unknownSavings.status()).isEqualTo(422);
		assertThat(field(unknownSavings)).isEqualTo("addToSavings");
		assertThat(as.get("/api/v1/me/settings").json().get("currentSavings").isNull()).isTrue();

		Response zero = as.post("/api/v1/month-review",
				Map.of("month", lastMonth.toString(), "addToSavings", money("0")));
		assertThat(zero.status()).isEqualTo(422);
		assertThat(review().get("status").asString()).as("failed attempts record nothing").isEqualTo("DUE");
	}

	@Test
	void aBadGoalUndoesTheWholeReview() {
		as.patch("/api/v1/me/settings", Map.of("currentSavings", money("80000")));
		String phone = goal("Phone", "80000", "20000");
		lastMonthKept();

		Response unknownGoal = as.post("/api/v1/month-review",
				Map.of("month", lastMonth.toString(), "addToSavings", money("5000"), "goalContributions",
						List.of(Map.of("goalId", phone, "amount", money("1000")),
								Map.of("goalId", UUID.randomUUID().toString(), "amount", money("1000")))));
		assertThat(unknownGoal.status()).isEqualTo(422);
		assertThat(field(unknownGoal)).isEqualTo("goalContributions[1].goalId");
		assertThat(savings()).isEqualTo("80000.00");
		assertThat(goalSaved(phone)).isEqualTo("20000.00");
		assertThat(review().get("status").asString()).isEqualTo("DUE");

		Response twice = as.post("/api/v1/month-review",
				Map.of("month", lastMonth.toString(), "goalContributions",
						List.of(Map.of("goalId", phone, "amount", money("1000")),
								Map.of("goalId", phone, "amount", money("1000")))));
		assertThat(twice.status()).isEqualTo(422);
		assertThat(field(twice)).isEqualTo("goalContributions[1].goalId");
	}

	@Test
	void reachingTheTargetAchievesTheGoal() {
		String phone = goal("Phone", "80000", "75000");
		lastMonthKept();
		as.post("/api/v1/month-review", Map.of("month", lastMonth.toString(), "goalContributions",
				List.of(Map.of("goalId", phone, "amount", money("5000")))));
		JsonNode goal = as.get("/api/v1/goals").json().get(0);
		assertThat(goal.get("status").asString()).isEqualTo("ACHIEVED");
		assertThat(goal.get("current").get("amount").asString()).isEqualTo("80000.00");
	}

}
