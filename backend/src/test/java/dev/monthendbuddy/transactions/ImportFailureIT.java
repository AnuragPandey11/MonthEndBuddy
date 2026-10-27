package dev.monthendbuddy.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import dev.monthendbuddy.common.events.EventPublisher;
import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.events.TransactionsImported;

/**
 * A commit that fails after its inserts were flushed leaves no transactions behind, is recorded as FAILED, and can be
 * retried (LLD §4.2). The failure is injected after the last batch, at the point where the event is published.
 */
class ImportFailureIT extends IntegrationTest {

	@MockitoSpyBean
	EventPublisher events;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void aFailedCommitWritesNothingIsMarkedFailedAndCanBeRetried() {
		ApiClient as = api().bearer(api().register().accessToken());
		String userId = as.get("/api/v1/me").json().get("id").asString();
		LocalDate day = TransactionFixtures.today().minusDays(30);
		StringBuilder csv = new StringBuilder("date,description,amount\n");
		for (int i = 0; i < 600; i++) {
			csv.append(day).append(",Item ").append(i).append(",-").append(10 + i).append('\n');
		}
		String jobId = as.uploadCsv(csv.toString()).json().get("id").asString();

		doThrow(new IllegalStateException("simulated failure after the inserts")).doCallRealMethod()
			.when(events)
			.publish(argThat(e -> e instanceof TransactionsImported));

		Response failed = as.post("/api/v1/imports/" + jobId + "/commit", null);
		assertThat(failed.status()).isEqualTo(500);
		assertThat(jdbc.queryForObject("select count(*) from transactions where user_id = ?::uuid", Integer.class,
				userId)).isZero();
		var job = as.get("/api/v1/imports/" + jobId).json();
		assertThat(job.get("status").asString()).isEqualTo("FAILED");
		assertThat(job.get("failureReason").asString()).isEqualTo("The import could not be saved");

		Response retried = as.post("/api/v1/imports/" + jobId + "/commit", null);
		assertThat(retried.status()).as(retried.body()).isEqualTo(200);
		assertThat(retried.json().get("imported").asInt()).isEqualTo(600);
		assertThat(jdbc.queryForObject("select count(*) from transactions where user_id = ?::uuid", Integer.class,
				userId)).isEqualTo(600);
	}

}
