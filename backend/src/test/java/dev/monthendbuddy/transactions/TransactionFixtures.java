package dev.monthendbuddy.transactions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/** Request bodies and IDs shared by the transactions integration tests. */
public final class TransactionFixtures {

	/** Seeded system categories (V2__transactions.sql). */
	public static final String DINING = "0192f000-0000-7000-8000-000000000003";

	public static final String GROCERIES = "0192f000-0000-7000-8000-000000000002";

	public static final String SALARY = "0192f000-0000-7000-8000-000000000101";

	private TransactionFixtures() {
	}

	/** "Today" for a new user, whose timezone defaults to Asia/Kolkata. */
	public static LocalDate today() {
		return LocalDate.now(ZoneId.of("Asia/Kolkata"));
	}

	public static Map<String, Object> transaction(String type, String amount, LocalDate date, String description,
			String merchant, String categoryId) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", type);
		body.put("amount", Map.of("amount", amount, "currency", "INR"));
		body.put("transactionDate", date.toString());
		body.put("description", description);
		body.put("merchant", merchant);
		body.put("categoryId", categoryId);
		return body;
	}

	public static Map<String, Object> expense(String amount, LocalDate date, String description) {
		return transaction("EXPENSE", amount, date, description, null, null);
	}

	public static Map<String, Object> withVersion(Map<String, Object> body, long version) {
		Map<String, Object> copy = new LinkedHashMap<>(body);
		copy.put("version", version);
		return copy;
	}

}
