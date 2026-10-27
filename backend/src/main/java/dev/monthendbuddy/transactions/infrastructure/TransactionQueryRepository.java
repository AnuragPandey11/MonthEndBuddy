package dev.monthendbuddy.transactions.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.Period;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.transactions.CategoryRef;
import dev.monthendbuddy.transactions.CategorySource;
import dev.monthendbuddy.transactions.TransactionFilter;
import dev.monthendbuddy.transactions.TransactionQueries;
import dev.monthendbuddy.transactions.TransactionSource;
import dev.monthendbuddy.transactions.TransactionType;
import dev.monthendbuddy.transactions.TransactionView;

/**
 * Filtered transaction reads in native SQL (LLD §4.1). Native SQL lets the text filter use exactly the indexed
 * expression {@code lower(description || ' ' || coalesce(merchant, ''))} and lets keyset pagination compare
 * {@code (transaction_date, id)} as a row value against the {@code (user_id, transaction_date DESC, id DESC)} index.
 * The SQL is built only from fixed fragments; every value is a bound parameter.
 */
@Repository
@Transactional(readOnly = true)
public class TransactionQueryRepository implements TransactionQueries {

	private static final String SELECT = """
			select t.id, t.type, t.amount, t.currency, t.transaction_date, t.description, t.merchant,
			       t.category_id, c.name as category_name, t.category_source, t.notes, t.source, t.version
			from transactions t
			left join categories c on c.id = t.category_id
			where t.user_id = :userId
			""";

	private final JdbcClient jdbc;

	public TransactionQueryRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<TransactionView> find(UserId user, TransactionFilter filter, int limit) {
		return page(user, filter, null, limit);
	}

	/** Rows strictly after {@code after} in (date desc, id desc) order, or from the start when it is null. */
	public List<TransactionView> page(UserId user, TransactionFilter filter, Keyset after, int limit) {
		StringBuilder sql = new StringBuilder(SELECT);
		Map<String, Object> params = new HashMap<>();
		params.put("userId", user.value());
		if (filter.from() != null) {
			sql.append(" and t.transaction_date >= :from");
			params.put("from", filter.from());
		}
		if (filter.to() != null) {
			sql.append(" and t.transaction_date <= :to");
			params.put("to", filter.to());
		}
		if (filter.categoryId() != null) {
			sql.append(" and t.category_id = :categoryId");
			params.put("categoryId", filter.categoryId());
		}
		if (filter.type() != null) {
			sql.append(" and t.type = :type");
			params.put("type", filter.type().name());
		}
		if (filter.text() != null) {
			sql.append(" and lower(t.description || ' ' || coalesce(t.merchant, '')) like :text escape '\\'");
			params.put("text", "%" + escapeLike(filter.text().toLowerCase(Locale.ROOT)) + "%");
		}
		if (filter.minAmount() != null) {
			sql.append(" and t.amount >= :minAmount");
			params.put("minAmount", filter.minAmount());
		}
		if (filter.maxAmount() != null) {
			sql.append(" and t.amount <= :maxAmount");
			params.put("maxAmount", filter.maxAmount());
		}
		if (after != null) {
			sql.append(" and (t.transaction_date, t.id) < (:afterDate, :afterId)");
			params.put("afterDate", after.transactionDate());
			params.put("afterId", after.id());
		}
		sql.append(" order by t.transaction_date desc, t.id desc limit :limit");
		params.put("limit", limit);
		return jdbc.sql(sql.toString()).params(params).query(TransactionQueryRepository::mapRow).list();
	}

	@Override
	public List<TransactionView> topExpenses(UserId user, Period period, UUID categoryId, int n) {
		return jdbc.sql(SELECT + """
				 and t.type = 'EXPENSE' and t.transaction_date between :from and :to
				 and (cast(:categoryId as uuid) is null or t.category_id = :categoryId)
				order by t.amount desc, t.transaction_date desc, t.id desc
				limit :n""")
			.param("userId", user.value())
			.param("from", period.startInclusive())
			.param("to", period.endInclusive())
			.param("categoryId", categoryId)
			.param("n", n)
			.query(TransactionQueryRepository::mapRow)
			.list();
	}

	@Override
	public Optional<TransactionView> findById(UserId user, UUID id) {
		return findOne(user, id);
	}

	public Optional<TransactionView> findOne(UserId user, UUID id) {
		return jdbc.sql(SELECT + " and t.id = :id")
			.param("userId", user.value())
			.param("id", id)
			.query(TransactionQueryRepository::mapRow)
			.optional();
	}

	private static TransactionView mapRow(ResultSet rs, int rowNum) throws SQLException {
		UUID categoryId = rs.getObject("category_id", UUID.class);
		return new TransactionView(rs.getObject("id", UUID.class), TransactionType.valueOf(rs.getString("type")),
				new Money(rs.getBigDecimal("amount"), CurrencyCode.valueOf(rs.getString("currency"))),
				rs.getObject("transaction_date", LocalDate.class), rs.getString("description"),
				rs.getString("merchant"), categoryId == null ? null : new CategoryRef(categoryId,
						rs.getString("category_name")),
				CategorySource.valueOf(rs.getString("category_source")), rs.getString("notes"),
				TransactionSource.valueOf(rs.getString("source")), rs.getLong("version"));
	}

	/** The (fingerprint, seq) pairs among {@code fingerprints} that the user already has (duplicate detection). */
	public Set<FingerprintKey> existingFingerprints(UserId user, Collection<String> fingerprints) {
		Set<FingerprintKey> found = new HashSet<>();
		List<String> all = List.copyOf(new LinkedHashSet<>(fingerprints));
		for (int start = 0; start < all.size(); start += 1000) {
			List<String> chunk = all.subList(start, Math.min(start + 1000, all.size()));
			found.addAll(jdbc.sql("""
					select fingerprint, fingerprint_seq from transactions
					where user_id = :userId and fingerprint in (:fingerprints)
					""")
				.param("userId", user.value())
				.param("fingerprints", chunk)
				.query((rs, i) -> new FingerprintKey(rs.getString(1), rs.getInt(2)))
				.list());
		}
		return found;
	}

	public record FingerprintKey(String fingerprint, int seq) {
	}

	/** Makes {@code %}, {@code _} and the escape character itself match literally. */
	static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/** The sort position of a row: (transaction_date, id), both descending. */
	public record Keyset(LocalDate transactionDate, UUID id) {
	}

}
