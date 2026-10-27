package dev.monthendbuddy.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.demo.SyntheticDataGenerator.GeneratedTransaction;
import dev.monthendbuddy.transactions.TransactionType;

class SampleCsvTest {

	@Test
	void writesTheImportFormatWithRfc4180Quoting() {
		String csv = SampleCsv.write(List.of(new GeneratedTransaction(LocalDate.of(2026, 9, 3), TransactionType.EXPENSE,
				new BigDecimal("1250.50"), "Dinner, with \"friends\"", "Truffles", "Dining")));
		assertThat(csv).isEqualTo("""
				date,description,amount,type,merchant,category,currency
				2026-09-03,"Dinner, with ""friends\""",1250.50,EXPENSE,Truffles,Dining,INR
				""");
	}

	@Test
	void emptyMerchantIsAnEmptyField() {
		assertThat(SampleCsv.quote(null)).isEmpty();
		assertThat(SampleCsv.quote("plain")).isEqualTo("plain");
	}

}
