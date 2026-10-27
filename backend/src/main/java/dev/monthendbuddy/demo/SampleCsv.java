package dev.monthendbuddy.demo;

import java.util.List;

import dev.monthendbuddy.demo.SyntheticDataGenerator.GeneratedTransaction;

/** Writes generated transactions in the v1 CSV import format (architecture §8.2), RFC 4180 quoting, LF line ends. */
public final class SampleCsv {

	public static final String HEADER = "date,description,amount,type,merchant,category,currency";

	private SampleCsv() {
	}

	public static String write(List<GeneratedTransaction> rows) {
		StringBuilder csv = new StringBuilder(HEADER).append('\n');
		for (GeneratedTransaction row : rows) {
			csv.append(row.date())
				.append(',')
				.append(quote(row.description()))
				.append(',')
				.append(row.amount().toPlainString())
				.append(',')
				.append(row.type())
				.append(',')
				.append(quote(row.merchant()))
				.append(',')
				.append(quote(row.category()))
				.append(",INR\n");
		}
		return csv.toString();
	}

	static String quote(String value) {
		if (value == null) {
			return "";
		}
		boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
				|| value.contains("\r");
		return needsQuotes ? '"' + value.replace("\"", "\"\"") + '"' : value;
	}

}
