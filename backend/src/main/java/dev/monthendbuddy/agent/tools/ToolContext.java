package dev.monthendbuddy.agent.tools;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.UserId;

/**
 * Everything a tool knows about the caller. The orchestrator fills it from {@code CurrentUser}; it is passed
 * explicitly, never read from a thread-local, so scoping holds on any thread (architecture §10.4). Tool inputs
 * never carry a user ID.
 */
public record ToolContext(UserId userId, ZoneId timezone, CurrencyCode currency, LocalDate today) {

	public YearMonth currentMonth() {
		return YearMonth.from(today);
	}

}
