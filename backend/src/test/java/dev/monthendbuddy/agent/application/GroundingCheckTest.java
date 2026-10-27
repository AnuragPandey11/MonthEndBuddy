package dev.monthendbuddy.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GroundingCheckTest {

	private final JsonMapper json = JsonMapper.builder().build();

	private final List<JsonNode> evidence = List.of(json.readTree("""
			{"data": {"total": {"amount": "48210.40", "currency": "INR"},
			          "categories": [{"name": "Dining", "amount": {"amount": "12450.00", "currency": "INR"}},
			                         {"name": "Rent", "amount": {"amount": "120000.00", "currency": "INR"}}],
			          "delta": {"amount": "-2500.00", "currency": "INR"}, "count": 87}}"""));

	@Test
	void amountsFromEvidencePass() {
		assertThat(GroundingCheck.unverifiedAmounts(
				"You spent ₹48,210.40 in total; Dining was ₹12,450 and rent Rs. 1,20,000. That is ₹2,500 less.",
				evidence, "where did my money go?"))
			.isEmpty();
	}

	@Test
	void roundedAmountsPassAtTheShownPrecision() {
		assertThat(GroundingCheck.unverifiedAmounts("About ₹48,210 total, or ₹1.2 lakh for rent and ₹48k overall.",
				evidence, ""))
			.isEmpty();
	}

	@Test
	void inventedAmountsAreFlagged() {
		assertThat(GroundingCheck.unverifiedAmounts("Dining was ₹12,450 and groceries ₹9,999.", evidence, ""))
			.containsExactly("₹9,999");
	}

	@Test
	void sumsTheModelWorkedOutItselfAreFlagged() {
		assertThat(GroundingCheck.unverifiedAmounts("Dining and rent came to ₹1,32,450.", evidence, ""))
			.containsExactly("₹1,32,450");
	}

	@Test
	void amountsTheUserStatedPass() {
		assertThat(GroundingCheck.unverifiedAmounts("A ₹60,000 phone would leave you short.", evidence,
				"can I afford a 60000 phone?"))
			.isEmpty();
	}

	@Test
	void aSmallDifferenceIsStillFlagged() {
		assertThat(GroundingCheck.unverifiedAmounts("Dining was ₹12,452.", evidence, "")).hasSize(1);
	}

}
