package dev.monthendbuddy.agent.tools;

import java.time.LocalDate;
import java.util.UUID;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.transactions.TransactionType;
import dev.monthendbuddy.transactions.TransactionView;

/**
 * A transaction as tools return it: description and merchant are untrusted user data (architecture §10.5), so they
 * are cleaned and cut to 120 characters, and notes are left out.
 */
public record ToolTransactionRow(UUID id, LocalDate date, TransactionType type, Money amount, String category,
		String description, String merchant) {

	static ToolTransactionRow of(TransactionView view) {
		return new ToolTransactionRow(view.id(), view.transactionDate(), view.type(), view.amount(),
				view.category() == null ? null : view.category().name(), Tools.text(view.description()),
				Tools.text(view.merchant()));
	}

}
