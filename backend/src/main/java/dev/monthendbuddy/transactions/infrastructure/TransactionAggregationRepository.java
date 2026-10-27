package dev.monthendbuddy.transactions.infrastructure;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.Period;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.transactions.TransactionAggregations;
import dev.monthendbuddy.transactions.TransactionType;

/**
 * The only class that issues aggregate SQL over {@code transactions} (LLD §4.1). Each method is one statement that
 * the database answers from the {@code (user_id, transaction_date …)} and {@code (user_id, category_id, …)} indexes;
 * nothing is loaded row by row into Java. {@code transaction_date} is a DATE, so no timezone conversion happens here.
 */
@Repository
@Transactional(readOnly = true)
public class TransactionAggregationRepository implements TransactionAggregations {

	private final JdbcClient jdbc;

	public TransactionAggregationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public TypeTotals totalsByType(UserId user, Period period) {
		return jdbc.sql("""
				select coalesce(sum(amount) filter (where type = 'INCOME'), 0)  as income,
				       count(*) filter (where type = 'INCOME')                   as income_count,
				       coalesce(sum(amount) filter (where type = 'EXPENSE'), 0) as expenses,
				       count(*) filter (where type = 'EXPENSE')                  as expense_count
				from transactions
				where user_id = :userId and transaction_date between :from and :to
				""")
			.param("userId", user.value())
			.param("from", period.startInclusive())
			.param("to", period.endInclusive())
			.query((rs, i) -> new TypeTotals(rs.getBigDecimal("income"), rs.getLong("income_count"),
					rs.getBigDecimal("expenses"), rs.getLong("expense_count")))
			.single();
	}

	@Override
	public List<CategoryTotal> totalsByCategory(UserId user, Period period, TransactionType type) {
		return jdbc.sql("""
				select t.category_id, c.name, sum(t.amount) as amount, count(*) as cnt
				from transactions t
				left join categories c on c.id = t.category_id
				where t.user_id = :userId and t.type = :type and t.transaction_date between :from and :to
				group by t.category_id, c.name
				order by amount desc, c.name nulls last
				""")
			.param("userId", user.value())
			.param("type", type.name())
			.param("from", period.startInclusive())
			.param("to", period.endInclusive())
			.query((rs, i) -> new CategoryTotal(rs.getObject("category_id", UUID.class), rs.getString("name"),
					rs.getBigDecimal("amount"), rs.getLong("cnt")))
			.list();
	}

	@Override
	public List<MonthTotal> monthlyTotals(UserId user, Period period) {
		return jdbc.sql("""
				select date_trunc('month', transaction_date)::date                as month,
				       coalesce(sum(amount) filter (where type = 'INCOME'), 0)  as income,
				       coalesce(sum(amount) filter (where type = 'EXPENSE'), 0) as expenses,
				       count(*)                                                  as cnt
				from transactions
				where user_id = :userId and transaction_date between :from and :to
				group by 1
				order by 1
				""")
			.param("userId", user.value())
			.param("from", period.startInclusive())
			.param("to", period.endInclusive())
			.query((rs, i) -> new MonthTotal(YearMonth.from(rs.getObject("month", LocalDate.class)),
					rs.getBigDecimal("income"), rs.getBigDecimal("expenses"), rs.getLong("cnt")))
			.list();
	}

	@Override
	public List<DatedAmount> categoryExpenses(UserId user, UUID categoryId, Period period) {
		return jdbc.sql("""
				select id, transaction_date, amount, coalesce(merchant, description) as label
				from transactions
				where user_id = :userId and category_id = :categoryId and type = 'EXPENSE'
				  and transaction_date between :from and :to
				order by transaction_date, id
				""")
			.param("userId", user.value())
			.param("categoryId", categoryId)
			.param("from", period.startInclusive())
			.param("to", period.endInclusive())
			.query((rs, i) -> new DatedAmount(rs.getObject("id", UUID.class),
					rs.getObject("transaction_date", LocalDate.class), rs.getBigDecimal("amount"), rs.getString("label")))
			.list();
	}

	@Override
	public DataCoverage coverage(UserId user, Period period) {
		return jdbc.sql("""
				select count(*)                                                  as cnt,
				       min(transaction_date)                                     as first_date,
				       max(transaction_date)                                     as last_date,
				       array_agg(distinct date_trunc('month', transaction_date)::date) as months
				from transactions
				where user_id = :userId and transaction_date between :from and :to
				""")
			.param("userId", user.value())
			.param("from", period.startInclusive())
			.param("to", period.endInclusive())
			.query((rs, i) -> {
				long count = rs.getLong("cnt");
				List<YearMonth> months = count == 0 ? List.of() : Arrays
					.stream((Object[]) rs.getArray("months").getArray())
					.map(m -> YearMonth.from(((Date) m).toLocalDate()))
					.sorted()
					.toList();
				return new DataCoverage(count, rs.getObject("first_date", LocalDate.class),
						rs.getObject("last_date", LocalDate.class), months);
			})
			.single();
	}

}
