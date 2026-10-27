package dev.monthendbuddy.transactions.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.transactions.infrastructure.CsvTransactionParser.RawCsvRow;

class CsvTransactionParserTest {

	private final CsvTransactionParser parser = new CsvTransactionParser();

	private List<RawCsvRow> parse(String csv) {
		return parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
	}

	private static String fileError(Runnable r) {
		try {
			r.run();
		}
		catch (ValidationException e) {
			assertThat(e.errors().get(0).field()).isEqualTo("file");
			return e.errors().get(0).message();
		}
		throw new AssertionError("expected a file error");
	}

	@Test
	void parsesQuotedFieldsCaseInsensitiveHeadersAndABom() {
		List<RawCsvRow> rows = parse("﻿Date, Description ,AMOUNT,Extra\n"
				+ "2026-09-03,\"Dinner, with \"\"friends\"\"\",-1250.50,ignored\n\n2026-09-04,Lunch,-300,x\n");
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0).rowNumber()).isEqualTo(2);
		assertThat(rows.get(0).get("description")).isEqualTo("Dinner, with \"friends\"");
		assertThat(rows.get(0).get("amount")).isEqualTo("-1250.50");
		assertThat(rows.get(1).rowNumber()).isEqualTo(3);
		assertThat(rows.get(1).get("merchant")).isNull();
	}

	@Test
	void missingRequiredColumnsAreNamed() {
		assertThat(fileError(() -> parse("date,details,value\n2026-09-03,x,1\n")))
			.contains("missing required columns: amount, description");
	}

	@Test
	void rejectsMoreThanTenThousandRows() {
		StringBuilder csv = new StringBuilder("date,description,amount\n");
		for (int i = 0; i < CsvTransactionParser.MAX_ROWS; i++) {
			csv.append("2026-09-03,Coffee,-10\n");
		}
		assertThat(parse(csv.toString())).hasSize(CsvTransactionParser.MAX_ROWS);
		csv.append("2026-09-03,One too many,-10\n");
		assertThat(fileError(() -> parse(csv.toString()))).contains("more than 10000 rows");
	}

	@Test
	void brokenQuotingAndNonUtf8AreFileErrors() {
		assertThat(fileError(() -> parse("date,description,amount\n2026-09-03,\"unterminated,-10\n")))
			.contains("not valid CSV");
		byte[] latin1 = "date,description,amount\n2026-09-03,Café,-10\n".getBytes(StandardCharsets.ISO_8859_1);
		assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(latin1)))
			.isInstanceOf(ValidationException.class)
			.satisfies(e -> assertThat(((ValidationException) e).errors().get(0).message()).contains("UTF-8"));
	}

}
