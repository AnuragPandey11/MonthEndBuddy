package dev.monthendbuddy.transactions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.transactions.MatchType;

class CategoryRuleTest {

	private static CategoryRule rule(MatchType type, String pattern) {
		return CategoryRule.create(new UserId(UUID.randomUUID()), type, pattern, UUID.randomUUID(), 100);
	}

	@Test
	void containsIsCaseAndSpacingInsensitiveOnDescriptionOrMerchant() {
		CategoryRule rule = rule(MatchType.CONTAINS, "  SWIGGY ");
		assertThat(rule.getPattern()).isEqualTo("swiggy");
		assertThat(rule.matches("Order from Swiggy Instamart", null)).isTrue();
		assertThat(rule.matches("Dinner", "swiggy")).isTrue();
		assertThat(rule.matches("Zomato", "Zomato")).isFalse();
	}

	@Test
	void startsWithAndEqualsCompareTheWholeNormalisedField() {
		assertThat(rule(MatchType.STARTS_WITH, "upi/bescom").matches("UPI/BESCOM/123", null)).isTrue();
		assertThat(rule(MatchType.STARTS_WITH, "bescom").matches("UPI/BESCOM/123", null)).isFalse();
		assertThat(rule(MatchType.EQUALS, "netflix").matches("Netflix", null)).isTrue();
		assertThat(rule(MatchType.EQUALS, "netflix").matches("Netflix subscription", "Netflix  ")).isTrue();
		assertThat(rule(MatchType.EQUALS, "netflix").matches("Netflix subscription", null)).isFalse();
	}

	@Test
	void blankPatternIsRejected() {
		assertThatThrownBy(() -> rule(MatchType.CONTAINS, " \t")).isInstanceOf(ValidationException.class);
	}

}
