package dev.monthendbuddy.agent.eval;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.TransactionSeeder;

/** Shared by the scripted (CI) and live eval runners: the fixture, the clock at the anchor date, and asking. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AiEvalSupport extends IntegrationTest {

	@Autowired
	TransactionSeeder seeder;

	AiEvalFixture fixture;

	EvalGrader grader;

	@BeforeEach
	void fixtureAtTheAnchorDate() {
		clock.setInstant(AiEvalFixture.NOW);
		if (fixture == null) {
			fixture = AiEvalFixture.seed(this::api, seeder);
			grader = new EvalGrader(asA(), fixture);
		}
	}

	List<EvalCase> cases() {
		return EvalCase.load();
	}

	ApiClient asA() {
		return api().bearer(fixture.tokenA);
	}

	/** Each case starts a new conversation, so no case sees another's history. */
	JsonNode ask(EvalCase c) {
		ApiClient.Response response = asA().post("/api/v1/agent/chat", Map.of("message", grader.fill(c.question())));
		if (response.status() != 200) {
			throw new AssertionError(c + ": HTTP " + response.status() + " " + response.body());
		}
		return response.json();
	}

}
