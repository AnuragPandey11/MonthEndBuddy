package dev.monthendbuddy.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Writes {@code sample-data/synthetic-transactions.csv}: {@code ./mvnw -Psample-data process-classes} (from
 * {@code backend/}). The anchor date is fixed so the committed file only changes when the generator does.
 */
public final class SampleDataExporter {

	public static final long SEED = 20_261_026L;

	public static final LocalDate ANCHOR = LocalDate.of(2026, 10, 15);

	private SampleDataExporter() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			throw new IllegalArgumentException("usage: SampleDataExporter <output.csv>");
		}
		var rows = new SyntheticDataGenerator().generate(new SyntheticDataGenerator.Spec(SEED, ANCHOR, 6, 0));
		Path out = Path.of(args[0]);
		Files.createDirectories(out.toAbsolutePath().getParent());
		Files.writeString(out, SampleCsv.write(rows), StandardCharsets.UTF_8);
		System.out.println("Wrote " + rows.size() + " transactions to " + out.toAbsolutePath().normalize());
	}

}
