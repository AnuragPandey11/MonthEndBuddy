package dev.monthendbuddy.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import dev.monthendbuddy.transactions.TransactionType;

/**
 * Deterministic synthetic transactions for one person: the same {@link Spec} always gives the same list, on any
 * machine ({@link Random} is fully specified by the JLS). Used for the demo account, the sample CSV and, from P3.6,
 * the AI evaluation fixtures. Amounts are INR and every category is a system category.
 *
 * <p>
 * Besides everyday spending, four anomalies are planted on purpose for the anomaly rules (architecture §9.5): an
 * unusually large dining bill last month, a ₹62,000 laptop two months ago, a trip that spikes this month's travel,
 * and an annual insurance premium in the first month.
 */
public final class SyntheticDataGenerator {

	/**
	 * @param seed fixes the random choices
	 * @param anchor the last date that may appear ("today"); nothing is generated after it
	 * @param months calendar months covered, ending with the anchor's month
	 * @param profile 0 or 1: two different households, for multi-user fixtures
	 */
	public record Spec(long seed, LocalDate anchor, int months, int profile) {

		public Spec {
			if (months < 4 || months > 24) {
				throw new IllegalArgumentException("months must be between 4 and 24");
			}
			if (profile < 0 || profile > 1) {
				throw new IllegalArgumentException("profile must be 0 or 1");
			}
		}

	}

	public record GeneratedTransaction(LocalDate date, TransactionType type, BigDecimal amount, String description,
			String merchant, String category) {
	}

	private record Pick(String merchant, String description) {
	}

	private static final List<Pick> GROCERIES = List.of(new Pick("BigBasket", "Weekly groceries"),
			new Pick("DMart", "Groceries"), new Pick("Zepto", "Quick groceries"),
			new Pick("Blinkit", "Milk and essentials"));

	private static final List<Pick> DINING = List.of(new Pick("Swiggy", "Food delivery"),
			new Pick("Zomato", "Food delivery"), new Pick("Third Wave Coffee", "Coffee"),
			new Pick("Chai Point", "Chai and snacks"), new Pick("Meghana Foods", "Lunch"),
			new Pick("Truffles", "Dinner"));

	private static final List<Pick> RIDES = List.of(new Pick("Uber", "Cab ride"), new Pick("Ola", "Auto ride"),
			new Pick("Namma Metro", "Metro card top-up"), new Pick("Rapido", "Bike taxi"));

	private static final List<Pick> SHOPPING = List.of(new Pick("Amazon", "Online order"),
			new Pick("Myntra", "Clothing"), new Pick("Flipkart", "Phone accessories"),
			new Pick("Decathlon", "Sports gear"));

	private static final List<Pick> ENTERTAINMENT = List.of(new Pick("BookMyShow", "Movie tickets"),
			new Pick("PVR Cinemas", "Movie"));

	private static final List<Pick> HEALTH = List.of(new Pick("Apollo Pharmacy", "Medicines"),
			new Pick("Practo", "Doctor consultation"));

	public List<GeneratedTransaction> generate(Spec spec) {
		Random random = new Random(spec.seed() * 31 + spec.profile());
		List<GeneratedTransaction> out = new ArrayList<>();
		LocalDate anchor = spec.anchor();
		YearMonth last = YearMonth.from(anchor);
		YearMonth first = last.minusMonths(spec.months() - 1L);
		int salary = spec.profile() == 0 ? 95_000 : 72_000;
		int rent = spec.profile() == 0 ? 25_000 : 18_000;

		for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
			String name = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
			Month m = new Month(month, anchor, random, out);
			m.fixed(1, TransactionType.INCOME, salary, "Salary " + name,
					spec.profile() == 0 ? "Acme Technologies" : "Globex Retail", "Salary");
			m.fixed(5, TransactionType.EXPENSE, rent, "Rent " + name, "Landlord (UPI)", "Rent");
			m.fixed(7, TransactionType.EXPENSE, 649, "Netflix subscription", "Netflix", "Subscriptions");
			m.fixed(9, TransactionType.EXPENSE, 119, "Spotify subscription", "Spotify", "Subscriptions");
			m.fixed(10, TransactionType.EXPENSE, 6_500, "Two-wheeler loan EMI", "Bajaj Finance", "EMI & Loans");
			m.fixed(12, TransactionType.EXPENSE, 999, "Broadband", "ACT Fibernet", "Utilities");
			m.fixed(14, TransactionType.EXPENSE, m.between(1_650, 2_650), "Electricity bill", "BESCOM", "Utilities");
			m.fixed(18, TransactionType.EXPENSE, 399, "Mobile recharge", "Jio", "Utilities");
			m.repeat(4, 6, GROCERIES, 600, 3_500, "Groceries");
			m.repeat(6, 10, DINING, 180, 1_400, "Dining");
			m.repeat(8, 12, RIDES, 60, 650, "Transport");
			m.repeat(1, 2, List.of(new Pick("Indian Oil", "Petrol")), 1_500, 3_000, "Transport");
			m.repeat(1, 3, SHOPPING, 500, 4_500, "Shopping");
			m.repeat(0, 2, ENTERTAINMENT, 300, 1_200, "Entertainment");
			m.repeat(0, 2, HEALTH, 200, 1_500, "Health");
			m.repeat(0, 1, List.of(new Pick("Naturals Salon", "Haircut")), 300, 900, "Personal Care");
			if (random.nextInt(3) == 0) {
				m.fixed(1 + random.nextInt(28), TransactionType.INCOME, m.between(8_000, 15_000),
						"Freelance project payment", "Upwork", "Freelance");
			}
			if (month.getMonthValue() % 3 == 0) {
				m.fixed(28, TransactionType.INCOME, m.between(300, 600), "Savings account interest", "HDFC Bank",
						"Interest");
			}
		}

		// Planted anomalies (see class comment).
		new Month(first, anchor, random, out).fixed(15, TransactionType.EXPENSE, 12_450, "Health insurance premium",
				"Star Health", "Insurance");
		new Month(last.minusMonths(2), anchor, random, out).fixed(20, TransactionType.EXPENSE, 62_000,
				"Laptop", "Croma", "Shopping");
		new Month(last.minusMonths(1), anchor, random, out).fixed(14, TransactionType.EXPENSE, 8_400,
				"Anniversary dinner", "The Leela Palace", "Dining");
		new Month(last, anchor, random, out).fixed(Math.min(2, anchor.getDayOfMonth()), TransactionType.EXPENSE, 18_000,
				"Weekend trip to Goa", "MakeMyTrip", "Travel");

		out.sort(Comparator.comparing(GeneratedTransaction::date));
		return List.copyOf(out);
	}

	/** Generation within one calendar month; dates after the anchor are dropped. */
	private record Month(YearMonth month, LocalDate anchor, Random random, List<GeneratedTransaction> out) {

		void fixed(int day, TransactionType type, int rupees, String description, String merchant, String category) {
			add(month.atDay(Math.min(day, month.lengthOfMonth())), type, BigDecimal.valueOf(rupees), description,
					merchant, category);
		}

		void repeat(int min, int max, List<Pick> picks, int minRupees, int maxRupees, String category) {
			int count = min + random.nextInt(max - min + 1);
			for (int i = 0; i < count; i++) {
				LocalDate date = month.atDay(1 + random.nextInt(month.lengthOfMonth()));
				Pick pick = picks.get(random.nextInt(picks.size()));
				BigDecimal amount = BigDecimal.valueOf(between(minRupees, maxRupees));
				if (random.nextInt(4) == 0) {
					amount = amount.add(new BigDecimal("0.50"));
				}
				add(date, TransactionType.EXPENSE, amount, pick.description(), pick.merchant(), category);
			}
		}

		int between(int min, int max) {
			return min + random.nextInt(max - min + 1);
		}

		private void add(LocalDate date, TransactionType type, BigDecimal amount, String description, String merchant,
				String category) {
			if (!date.isAfter(anchor)) {
				out.add(new GeneratedTransaction(date, type, amount.setScale(2), description, merchant, category));
			}
		}

	}

}
