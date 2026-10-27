package dev.monthendbuddy.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.TransactionFixtures;

/**
 * A failing after-commit handler must not fail the user's write, which has already committed (implementation plan
 * P2.5/P2.8): the handler's own transaction rolls back and the error is logged.
 */
class AnomalyHandlerFailureIT extends IntegrationTest {

	@MockitoSpyBean
	AnomalyService anomalies;

	@Test
	void theWriteSucceedsEvenIfAnomalyEvaluationFails() {
		doThrow(new IllegalStateException("simulated rule failure")).when(anomalies).evaluate(any(), any());
		ApiClient as = api().bearer(api().register().accessToken());
		var created = as.post("/api/v1/transactions",
				TransactionFixtures.expense("30000", TransactionFixtures.today(), "Big purchase"));
		assertThat(created.status()).isEqualTo(201);
		assertThat(as.get("/api/v1/transactions/" + created.json().get("id").asString()).status()).isEqualTo(200);
		assertThat(as.get("/api/v1/alerts").json()).isEmpty();
	}

}
