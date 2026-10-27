package dev.monthendbuddy.agent.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * Flags money amounts in the model's answer that appear in none of the turn's data (architecture §10.6). It was made
 * a Phase 3 requirement after the provider spike saw the model invent a table of transactions following a tool error
 * (ADR 0001). An amount counts as grounded when it matches, at the precision the answer shows, a number in a tool
 * result, a tool argument or the user's own message. Sums the model works out itself are flagged too, on purpose:
 * the backend did not compute them.
 */
final class GroundingCheck {

	private static final Pattern AMOUNT = Pattern.compile(
			"(?:₹|\\bRs\\.?|\\bINR)\\s?(\\d[\\d,]*(?:\\.\\d+)?)(?:\\s?(lakhs?|lacs?|crores?|cr|k)\\b)?",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern NUMBER = Pattern.compile("-?\\d[\\d,]*(?:\\.\\d+)?");

	private GroundingCheck() {
	}

	/** The amounts, as written in {@code answer}, that match no known value. */
	static List<String> unverifiedAmounts(String answer, List<JsonNode> data, String userMessage) {
		if (answer == null || answer.isBlank()) {
			return List.of();
		}
		Set<BigDecimal> known = new LinkedHashSet<>();
		data.forEach(node -> collect(node, known));
		collectText(userMessage, known);
		List<String> unverified = new ArrayList<>();
		Matcher m = AMOUNT.matcher(answer);
		while (m.find()) {
			BigDecimal value = parse(m.group(1));
			if (value == null) {
				continue;
			}
			BigDecimal multiplier = multiplier(m.group(2));
			BigDecimal amount = value.multiply(multiplier);
			// Half a unit of the last digit shown, e.g. ±0.5 for "₹12,450" and ±5,000 for "₹1.2 lakh".
			BigDecimal tolerance = BigDecimal.ONE.movePointLeft(Math.max(0, value.scale()))
				.multiply(multiplier)
				.divide(BigDecimal.valueOf(2))
				.add(new BigDecimal("0.005"));
			if (known.stream().noneMatch(k -> k.subtract(amount).abs().compareTo(tolerance) <= 0)) {
				unverified.add(m.group().strip());
			}
		}
		return unverified.stream().distinct().toList();
	}

	private static void collect(JsonNode node, Set<BigDecimal> known) {
		if (node == null) {
			return;
		}
		if (node.isNumber()) {
			known.add(node.decimalValue().abs());
		}
		else if (node.isString()) {
			collectText(node.asString(), known);
		}
		else {
			node.forEach(child -> collect(child, known));
		}
	}

	private static void collectText(String text, Set<BigDecimal> known) {
		if (text == null) {
			return;
		}
		Matcher amounts = AMOUNT.matcher(text);
		while (amounts.find()) {
			BigDecimal value = parse(amounts.group(1));
			if (value != null) {
				known.add(value.multiply(multiplier(amounts.group(2))));
			}
		}
		Matcher numbers = NUMBER.matcher(text);
		while (numbers.find()) {
			BigDecimal value = parse(numbers.group());
			if (value != null) {
				known.add(value.abs());
			}
		}
	}

	private static BigDecimal multiplier(String suffix) {
		if (suffix == null) {
			return BigDecimal.ONE;
		}
		String s = suffix.toLowerCase(Locale.ROOT);
		if (s.startsWith("l")) {
			return BigDecimal.valueOf(100_000);
		}
		if (s.startsWith("cr")) {
			return BigDecimal.valueOf(10_000_000);
		}
		return BigDecimal.valueOf(1_000);
	}

	private static BigDecimal parse(String digits) {
		try {
			return new BigDecimal(digits.replace(",", ""));
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
