package dev.monthendbuddy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.IntegrationTest;

class ApplicationIT extends IntegrationTest {

	private final HttpClient http = HttpClient.newHttpClient();

	@LocalManagementPort
	int managementPort;

	@Autowired
	Flyway flyway;

	@Test
	void readinessAndLivenessAreUpOnTheManagementPortWithoutAuthentication() throws Exception {
		assertThat(get(managementPort, "/actuator/health/readiness")).isEqualTo(200);
		assertThat(get(managementPort, "/actuator/health/liveness")).isEqualTo(200);
	}

	@Test
	void actuatorIsNotServedOnTheApiPort() throws Exception {
		assertThat(apiPort).isNotEqualTo(managementPort);
		String token = api().register().accessToken();
		assertThat(api().bearer(token).get("/actuator/health").status()).isEqualTo(404);
	}

	@Test
	void flywayAppliedTheIdentityMigration() {
		assertThat(flyway.info().applied()).extracting(m -> m.getVersion().getVersion()).contains("1");
	}

	@Test
	void applicationJsonMapperWritesMoneyAsAString() {
		assertThat(jsonMapper.writeValueAsString(Money.of("99.5", CurrencyCode.INR)))
			.isEqualTo("{\"amount\":\"99.50\",\"currency\":\"INR\"}");
	}

	@Test
	void unknownApiPathIsANotFoundProblemCarryingTheResponseTraceId() {
		String token = api().register().accessToken();
		ApiClient.Response response = api().bearer(token).get("/api/v1/nope");
		String traceId = response.header("X-Trace-Id").orElseThrow();
		assertThat(response.status()).isEqualTo(404);
		assertThat(response.header("Content-Type")).hasValue("application/problem+json");
		assertThat(response.json().get("code").asString()).isEqualTo("not_found");
		assertThat(response.json().get("traceId").asString()).isEqualTo(traceId);
	}

	@Test
	void unauthenticatedRequestsGetAnUnauthorizedProblemNotA404() {
		ApiClient.Response response = api().get("/api/v1/nope");
		assertThat(response.status()).isEqualTo(401);
		assertThat(response.json().get("code").asString()).isEqualTo("unauthorized");
		assertThat(response.json().get("traceId").asString()).isEqualTo(response.header("X-Trace-Id").orElseThrow());
	}

	private int get(int port, String path) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
		return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

}
