package dev.monthendbuddy.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.application.ImportService;

/** CSV import end to end, including every duplicate scenario of architecture §8.3 (implementation plan P2.4). */
class ImportApiIT extends IntegrationTest {

	private static final String HEADER = "date,description,amount,merchant,category\n";

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ImportService imports;

	private ApiClient as;

	private final YearMonth jan = YearMonth.from(TransactionFixtures.today()).minusMonths(3);

	private final YearMonth feb = jan.plusMonths(1);

	private final YearMonth mar = feb.plusMonths(1);

	@BeforeEach
	void newUser() {
		as = api().bearer(api().register().accessToken());
	}

	private JsonNode upload(String csv) {
		Response response = as.uploadCsv(csv);
		assertThat(response.status()).as(response.body()).isEqualTo(201);
		return response.json();
	}

	private JsonNode commit(String jobId, Object body) {
		Response response = as.post("/api/v1/imports/" + jobId + "/commit", body);
		assertThat(response.status()).as(response.body()).isEqualTo(200);
		return response.json();
	}

	private List<JsonNode> transactions() {
		List<JsonNode> all = new ArrayList<>();
		as.get("/api/v1/transactions?limit=100").json().get("items").forEach(all::add);
		return all;
	}

	private String rowsFor(YearMonth month, String prefix) {
		return month.atDay(3) + "," + prefix + " groceries,-1200.00,BigBasket,Groceries\n" + month.atDay(10) + ","
				+ prefix + " dinner,-850.50,Truffles,Dining\n" + month.atDay(1) + ",Salary " + prefix
				+ ",95000,Acme,Salary\n";
	}

	@Test
	void uploadStagesRowsAndReportsRowLevelErrorsBeforeAnythingIsImported() {
		JsonNode job = upload(HEADER + rowsFor(jan, "Jan") + "not-a-date,Broken,-10,,\n" + jan.atDay(4)
				+ ",Zero,0,,\n" + jan.atDay(5) + ",Unknown category,-20,,Pets\n");
		assertThat(job.get("status").asString()).isEqualTo("VALIDATED");
		assertThat(job.get("totals").get("total").asInt()).isEqualTo(6);
		assertThat(job.get("totals").get("valid").asInt()).isEqualTo(4);
		assertThat(job.get("totals").get("invalid").asInt()).isEqualTo(2);
		assertThat(job.get("sampleErrors").get(0).get("rowNumber").asInt()).isEqualTo(5);
		assertThat(job.get("sampleErrors").get(0).get("field").asString()).isEqualTo("date");
		assertThat(transactions()).isEmpty();

		JsonNode invalid = as.get("/api/v1/imports/" + job.get("id").asString() + "/rows?status=INVALID").json();
		assertThat(invalid).hasSize(2);
		assertThat(invalid.get(1).get("errors").get(0).get("field").asString()).isEqualTo("amount");
		JsonNode valid = as.get("/api/v1/imports/" + job.get("id").asString() + "/rows?status=VALID").json();
		assertThat(valid.get(3).get("warnings").get(0).get("field").asString()).isEqualTo("category");
	}

	@Test
	void commitImportsValidRowsWithTheirCategoriesAndIsIdempotent() {
		String jobId = upload(HEADER + rowsFor(jan, "Jan") + jan.atDay(5) + ",Unknown category,-20,,Pets\n")
			.get("id")
			.asString();
		JsonNode result = commit(jobId, null);
		assertThat(result.get("imported").asInt()).isEqualTo(4);
		assertThat(result.get("skippedDuplicates").asInt()).isZero();

		List<JsonNode> imported = transactions();
		assertThat(imported).hasSize(4).allSatisfy(t -> assertThat(t.get("source").asString()).isEqualTo("CSV_IMPORT"));
		JsonNode dinner = imported.stream().filter(t -> t.get("description").asString().equals("Jan dinner")).findFirst()
			.orElseThrow();
		assertThat(dinner.get("type").asString()).isEqualTo("EXPENSE");
		assertThat(dinner.get("amount").get("amount").asString()).isEqualTo("850.50");
		assertThat(dinner.get("category").get("name").asString()).isEqualTo("Dining");
		assertThat(dinner.get("categorySource").asString()).isEqualTo("IMPORT");

		assertThat(commit(jobId, null)).isEqualTo(result);
		assertThat(transactions()).hasSize(4);
		assertThat(as.get("/api/v1/imports/" + jobId).json().get("status").asString()).isEqualTo("COMMITTED");
	}

	@Test
	void theSameStatementTwiceIsAllDuplicates() {
		String csv = HEADER + rowsFor(jan, "Jan");
		commit(upload(csv).get("id").asString(), null);
		JsonNode again = upload(csv);
		assertThat(again.get("totals").get("duplicate").asInt()).isEqualTo(3);
		assertThat(again.get("totals").get("valid").asInt()).isZero();
		JsonNode result = commit(again.get("id").asString(), null);
		assertThat(result.get("imported").asInt()).isZero();
		assertThat(result.get("skippedDuplicates").asInt()).isEqualTo(3);
		assertThat(transactions()).hasSize(3);
	}

	@Test
	void overlappingStatementsImportOnlyTheNewMonth() {
		commit(upload(HEADER + rowsFor(jan, "Jan") + rowsFor(feb, "Feb")).get("id").asString(), null);
		JsonNode overlap = upload(HEADER + rowsFor(feb, "Feb") + rowsFor(mar, "Mar"));
		assertThat(overlap.get("totals").get("duplicate").asInt()).isEqualTo(3);
		assertThat(overlap.get("totals").get("valid").asInt()).isEqualTo(3);
		assertThat(commit(overlap.get("id").asString(), null).get("imported").asInt()).isEqualTo(3);
		assertThat(transactions()).hasSize(9);
	}

	@Test
	void twoGenuineIdenticalCoffeesInOneFileAreBothImported() {
		String coffees = HEADER + jan.atDay(7) + ",Coffee,-120,Chai Point,Dining\n" + jan.atDay(7)
				+ ",Coffee,-120,Chai Point,Dining\n";
		JsonNode job = upload(coffees);
		assertThat(job.get("totals").get("valid").asInt()).isEqualTo(2);
		commit(job.get("id").asString(), null);
		assertThat(transactions()).hasSize(2);
		// Uploading the same file again matches row for row.
		assertThat(upload(coffees).get("totals").get("duplicate").asInt()).isEqualTo(2);
	}

	@Test
	void aDuplicateCanBeImportedAnywayAndIsThenOutsideTheDuplicateIndex() {
		String csv = HEADER + jan.atDay(7) + ",Coffee,-120,Chai Point,Dining\n";
		commit(upload(csv).get("id").asString(), null);
		JsonNode again = upload(csv);
		String jobId = again.get("id").asString();
		String rowId = as.get("/api/v1/imports/" + jobId + "/rows?status=DUPLICATE").json().get(0).get("id").asString();
		assertThat(commit(jobId, Map.of("forceRowIds", List.of(rowId))).get("imported").asInt()).isEqualTo(1);
		assertThat(transactions()).hasSize(2);
		assertThat(jdbc.queryForObject("select count(*) from transactions where import_job_id = ?::uuid and fingerprint is null",
				Integer.class, jobId)).isEqualTo(1);

		Response bogus = as.post("/api/v1/imports/" + upload(csv).get("id").asString() + "/commit",
				Map.of("forceRowIds", List.of(java.util.UUID.randomUUID())));
		assertThat(bogus.status()).isEqualTo(422);
	}

	@Test
	void manuallyEnteredTransactionsAreNotDetectedAsDuplicates() {
		as.post("/api/v1/transactions", TransactionFixtures.expense("120", jan.atDay(7), "Coffee"));
		assertThat(upload(HEADER + jan.atDay(7) + ",Coffee,-120,,\n").get("totals").get("valid").asInt()).isEqualTo(1);
	}

	@Test
	void aDiscardedImportCannotBeCommitted() {
		String jobId = upload(HEADER + rowsFor(jan, "Jan")).get("id").asString();
		assertThat(as.delete("/api/v1/imports/" + jobId).status()).isEqualTo(204);
		assertThat(as.get("/api/v1/imports/" + jobId + "/rows").json()).isEmpty();
		Response commit = as.post("/api/v1/imports/" + jobId + "/commit", null);
		assertThat(commit.status()).isEqualTo(409);
		assertThat(commit.json().get("code").asString()).isEqualTo("invalid_state");
		assertThat(as.delete("/api/v1/imports/" + jobId).status()).isEqualTo(409);
	}

	@Test
	void fileLevelProblemsAreRejected() {
		assertFileError(as.uploadCsv("date,details,value\n2026-01-01,x,1\n"), "missing required columns");
		assertFileError(as.uploadCsv(HEADER), "no rows");
		assertFileError(as.upload("/api/v1/imports", "statement.xlsx", "application/vnd.ms-excel",
				"x".getBytes(StandardCharsets.UTF_8)), ".csv");
		assertThat(as.upload("/api/v1/imports", "big.csv", "text/csv", new byte[6 * 1024 * 1024]).status())
			.isEqualTo(413);
	}

	@Test
	void ddMmYyyyDatesAreSupported() {
		JsonNode job = as.upload("/api/v1/imports?dateFormat=DD_MM_YYYY", "s.csv", "text/csv",
				("date,description,amount\n" + jan.atDay(9).getDayOfMonth() + "/" + jan.getMonthValue() + "/"
						+ jan.getYear() + ",Bus,-40\n").getBytes(StandardCharsets.UTF_8))
			.json();
		assertThat(job.get("totals").get("valid").asInt()).isEqualTo(1);
		assertThat(job.get("dateFormat").asString()).isEqualTo("DD_MM_YYYY");
	}

	@Test
	void stagedRowsArePurgedAfterSevenDays() {
		String jobId = upload(HEADER + rowsFor(jan, "Jan")).get("id").asString();
		jdbc.update("update import_jobs set updated_at = now() - interval '8 days' where id = ?::uuid", jobId);
		assertThat(imports.purgeStaleRows()).isGreaterThanOrEqualTo(3);
		assertThat(as.get("/api/v1/imports/" + jobId).json().get("status").asString()).isEqualTo("DISCARDED");
	}

	/** Implementation plan P2.4 "done when". */
	@Test
	void theSampleStatementImportsOnceAndIsAllDuplicatesTheSecondTime() throws Exception {
		byte[] sample = Files.readAllBytes(Path.of("..", "sample-data", "synthetic-transactions.csv"));
		JsonNode first = as.upload("/api/v1/imports", "synthetic-transactions.csv", "text/csv", sample).json();
		assertThat(first.get("totals").get("valid").asInt()).isEqualTo(214);
		assertThat(commit(first.get("id").asString(), null).get("imported").asInt()).isEqualTo(214);

		JsonNode second = as.upload("/api/v1/imports", "synthetic-transactions.csv", "text/csv", sample).json();
		assertThat(second.get("totals").get("duplicate").asInt()).isEqualTo(214);
		assertThat(second.get("totals").get("valid").asInt()).isZero();
		assertThat(Set.of(commit(second.get("id").asString(), null).get("imported").asInt())).containsExactly(0);
	}

	private static void assertFileError(Response response, String messagePart) {
		assertThat(response.status()).as(response.body()).isEqualTo(422);
		assertThat(response.json().get("errors").get(0).get("field").asString()).isEqualTo("file");
		assertThat(response.json().get("errors").get(0).get("message").asString()).contains(messagePart);
	}

}
