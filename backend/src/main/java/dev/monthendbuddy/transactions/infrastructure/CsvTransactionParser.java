package dev.monthendbuddy.transactions.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import dev.monthendbuddy.common.error.ValidationException;

/**
 * Streams a CSV statement (architecture §8.2): UTF-8 (a BOM is tolerated), a header row, quoted fields with commas.
 * Header names are matched case-insensitively and unknown columns are ignored. File-level problems (no required
 * columns, too many rows, broken quoting, not UTF-8) are a 422 on {@code file}; row-level problems are left to
 * {@code RowValidator}.
 */
@Component
public class CsvTransactionParser {

	public static final int MAX_ROWS = 10_000;

	public static final Set<String> REQUIRED_COLUMNS = Set.of("date", "description", "amount");

	private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
		.setHeader()
		.setSkipHeaderRecord(true)
		.setIgnoreEmptyLines(true)
		.setTrim(true)
		.setAllowMissingColumnNames(true)
		.get();

	/** A data row; {@code rowNumber} matches a spreadsheet where row 1 is the header. */
	public record RawCsvRow(int rowNumber, Map<String, String> values) {

		public String get(String column) {
			String value = values.get(column);
			return value == null || value.isBlank() ? null : value;
		}

	}

	public List<RawCsvRow> parse(InputStream input) {
		try (Reader reader = withoutBom(new InputStreamReader(input,
				StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)));
				CSVParser parser = FORMAT.parse(reader)) {
			Map<String, String> columns = new HashMap<>();
			for (String header : parser.getHeaderNames()) {
				if (header != null && !header.isBlank()) {
					columns.putIfAbsent(header.trim().toLowerCase(Locale.ROOT), header);
				}
			}
			Set<String> missing = new TreeSet<>(REQUIRED_COLUMNS);
			missing.removeAll(columns.keySet());
			if (!missing.isEmpty()) {
				throw ValidationException.of("file", "is missing required columns: " + String.join(", ", missing)
						+ " (expected a header row with date, description, amount)");
			}
			List<RawCsvRow> rows = new ArrayList<>();
			int rowNumber = 1;
			for (CSVRecord record : parser) {
				rowNumber++;
				if (rows.size() == MAX_ROWS) {
					throw ValidationException.of("file", "has more than " + MAX_ROWS + " rows");
				}
				Map<String, String> values = new HashMap<>();
				columns.forEach((normalized, original) -> {
					if (record.isMapped(original) && record.isSet(original)) {
						values.put(normalized, record.get(original));
					}
				});
				rows.add(new RawCsvRow(rowNumber, values));
			}
			return rows;
		}
		catch (CharacterCodingException e) {
			throw ValidationException.of("file", "is not UTF-8 text");
		}
		catch (UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
			Throwable cause = e.getCause() instanceof CharacterCodingException ? e.getCause() : e;
			throw ValidationException.of("file", cause instanceof CharacterCodingException ? "is not UTF-8 text"
					: "is not valid CSV (check quoting near " + describe(e) + ")");
		}
		catch (IOException e) {
			throw ValidationException.of("file", "could not be read");
		}
	}

	private static String describe(RuntimeException e) {
		String message = e.getMessage();
		return message == null ? "the reported line" : message.replaceAll("[\\r\\n]+", " ");
	}

	private static Reader withoutBom(Reader reader) throws IOException {
		PushbackReader pushback = new PushbackReader(reader, 1);
		int first = pushback.read();
		if (first != -1 && first != '﻿') {
			pushback.unread(first);
		}
		return pushback;
	}

}
