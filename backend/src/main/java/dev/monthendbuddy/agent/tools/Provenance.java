package dev.monthendbuddy.agent.tools;

import java.time.Instant;

import dev.monthendbuddy.common.CurrencyCode;

/**
 * Where a tool's figures come from (architecture §10.4): the data source, the period they cover, and how many
 * transactions they rest on, so "no transactions recorded" is never mistaken for zero spending.
 */
public record Provenance(String source, String period, CurrencyCode currency, Instant asOf, Long transactionCount) {

}
