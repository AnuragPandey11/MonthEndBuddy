package dev.monthendbuddy.demo;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.budgets.BudgetCommands;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.DemoDataReset;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.goals.GoalCommands;
import dev.monthendbuddy.identity.DemoAccounts;
import dev.monthendbuddy.transactions.CategoryQueries;
import dev.monthendbuddy.transactions.SeedTransaction;
import dev.monthendbuddy.transactions.TransactionSeeder;

/**
 * Seeds the demo account with six months of synthetic data ending today (Asia/Kolkata), at every startup and every
 * night, which also wipes whatever demo visitors changed (architecture §5.2). Reseeding at startup keeps the data
 * anchored to today and covered by every feature after a deploy; it takes well under a second.
 */
@Service
@EnableConfigurationProperties(DemoProperties.class)
@ConditionalOnProperty(name = "meb.demo.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataService {

	static final ZoneId DEMO_ZONE = ZoneId.of("Asia/Kolkata");

	private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);

	private final DemoAccounts accounts;

	private final TransactionSeeder seeder;

	private final List<DemoDataReset> moduleResets;

	private final BudgetCommands budgets;

	private final GoalCommands goals;

	private final CategoryQueries categories;

	private final DemoProperties properties;

	private final Clock clock;

	private final SyntheticDataGenerator generator = new SyntheticDataGenerator();

	DemoDataService(DemoAccounts accounts, TransactionSeeder seeder, List<DemoDataReset> moduleResets,
			BudgetCommands budgets, GoalCommands goals, CategoryQueries categories, DemoProperties properties,
			Clock clock) {
		this.accounts = accounts;
		this.seeder = seeder;
		this.moduleResets = moduleResets;
		this.budgets = budgets;
		this.goals = goals;
		this.categories = categories;
		this.properties = properties;
		this.clock = clock;
	}

	/** Recreates the demo data in one transaction; visitors never see a half-reset account. */
	@Transactional
	public int resetDemoUser() {
		UserId demo = accounts.resetDemoAccount(new Money(properties.currentSavings(), CurrencyCode.INR));
		LocalDate today = LocalDate.now(clock.withZone(DEMO_ZONE));
		List<SeedTransaction> rows = generator
			.generate(new SyntheticDataGenerator.Spec(properties.seed(), today, 6, 0))
			.stream()
			.map(g -> new SeedTransaction(g.type(), new Money(g.amount(), CurrencyCode.INR), g.date(), g.description(),
					g.merchant(), g.category()))
			.toList();
		moduleResets.forEach(reset -> reset.deleteDemoData(demo));
		int inserted = seeder.reseed(demo, rows);
		seedPlans(demo, today);
		log.info("Demo account reset with {} synthetic transactions ending {}", inserted, today);
		return inserted;
	}

	/**
	 * Budgets and goals so every page has something to show. The dining budget is deliberately tight: the planted
	 * anniversary dinner pushes last month over it.
	 */
	private void seedPlans(UserId demo, LocalDate today) {
		budgets.createBudget(demo, null, inr("85000"), List.of(80, 100));
		for (String[] budget : new String[][] { { "Dining", "8000" }, { "Groceries", "11000" }, { "Shopping", "6000" } }) {
			categories.findSystem(budget[0])
				.ifPresent(c -> budgets.createBudget(demo, c.id(), inr(budget[1]), List.of(80, 100)));
		}
		goals.createGoal(demo, "Emergency fund", inr("300000"), inr("185000"), today.plusMonths(10).withDayOfMonth(1));
		goals.createGoal(demo, "New laptop", inr("90000"), inr("22000"), today.plusMonths(6).withDayOfMonth(1));
	}

	private static Money inr(String amount) {
		return Money.of(amount, CurrencyCode.INR);
	}

	@EventListener(ApplicationReadyEvent.class)
	void resetOnStartup() {
		resetDemoUser();
	}

	@Scheduled(cron = "${meb.demo.reset-cron:0 0 3 * * *}", zone = "Asia/Kolkata")
	void nightlyReset() {
		resetDemoUser();
	}

}
