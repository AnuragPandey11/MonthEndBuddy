package dev.monthendbuddy.budgets.infrastructure;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Records which budget thresholds have fired in which month. */
@Repository
public class ThresholdEventRepository {

	private final JdbcClient jdbc;

	public ThresholdEventRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** @return true only the first time this threshold fires for this budget and month */
	public boolean recordFirstFiring(UUID budgetId, LocalDate periodStart, int threshold) {
		return jdbc.sql("""
				insert into budget_threshold_events (budget_id, period_start, threshold, fired_at)
				values (:budgetId, :periodStart, :threshold, now())
				on conflict do nothing
				""")
			.param("budgetId", budgetId)
			.param("periodStart", periodStart)
			.param("threshold", threshold)
			.update() == 1;
	}

}
