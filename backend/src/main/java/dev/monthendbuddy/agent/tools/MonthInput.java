package dev.monthendbuddy.agent.tools;

import java.time.YearMonth;

import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** A single calendar month; omitted means the current month in the user's timezone. */
public record MonthInput(@JsonProperty(required = false) @JsonPropertyDescription("Month as YYYY-MM. Omit for the current month.")
		@Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "must be a month in YYYY-MM format") String month) {

	YearMonth resolve(ToolContext context) {
		return month == null ? context.currentMonth() : YearMonth.parse(month);
	}

}
