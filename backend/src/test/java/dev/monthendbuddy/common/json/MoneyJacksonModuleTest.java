package dev.monthendbuddy.common.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.web.MoneyDto;

class MoneyJacksonModuleTest {

	private final JsonMapper mapper = JsonMapper.builder().addModule(new MoneyJacksonModule()).build();

	@Test
	void moneyIsWrittenWithAStringAmountAtScaleTwo() {
		String json = mapper.writeValueAsString(Money.of("12450", CurrencyCode.INR));
		assertThat(json).isEqualTo("{\"amount\":\"12450.00\",\"currency\":\"INR\"}");
	}

	@Test
	void moneyDtoHasTheSameShape() {
		String json = mapper.writeValueAsString(MoneyDto.from(Money.of("0.135", CurrencyCode.INR)));
		assertThat(json).isEqualTo("{\"amount\":\"0.14\",\"currency\":\"INR\"}");
	}

}
