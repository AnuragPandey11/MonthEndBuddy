package dev.monthendbuddy.analytics.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.monthendbuddy.analytics.AlertStatus;
import dev.monthendbuddy.analytics.RuleId;
import dev.monthendbuddy.analytics.Severity;
import dev.monthendbuddy.analytics.SubjectType;
import dev.monthendbuddy.analytics.domain.anomaly.AnomalyFinding;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.Uuids;

/** Anomaly alerts, every statement scoped to the user. */
@Repository
public class AlertRepository {

	private static final String COLUMNS = """
			select id, rule_id, subject_type, subject_id, period_start, severity, explanation, status, created_at
			from anomaly_alerts where user_id = :userId""";

	private final JdbcClient jdbc;

	public AlertRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public record AlertRow(UUID id, RuleId ruleId, SubjectType subjectType, UUID subjectId, LocalDate periodStart,
			Severity severity, String explanationJson, AlertStatus status, Instant createdAt) {
	}

	/**
	 * Inserts the alert unless one exists for the same rule, subject and month (open, dismissed or expected).
	 * @return the new alert's id, or empty when it already existed
	 */
	public Optional<UUID> insertIfNew(UserId user, AnomalyFinding finding, String explanationJson) {
		return jdbc.sql("""
				insert into anomaly_alerts (id, user_id, rule_id, subject_type, subject_id, period_start, severity,
				                            explanation, status, created_at, updated_at, version)
				values (:id, :userId, :ruleId, :subjectType, :subjectId, :periodStart, :severity, :explanation, 'OPEN',
				        now(), now(), 0)
				on conflict (user_id, rule_id, subject_type, subject_id, period_start) do nothing
				returning id
				""")
			.param("id", Uuids.v7())
			.param("userId", user.value())
			.param("ruleId", finding.ruleId().name())
			.param("subjectType", finding.subjectType().name())
			.param("subjectId", finding.subjectId())
			.param("periodStart", finding.periodStart())
			.param("severity", finding.severity().name())
			.param("explanation", explanationJson)
			.query(UUID.class)
			.optional();
	}

	public List<AlertRow> find(UserId user, AlertStatus status, LocalDate periodStart) {
		return jdbc.sql(COLUMNS + """
				 and (cast(:status as text) is null or status = :status)
				 and (cast(:periodStart as date) is null or period_start = :periodStart)
				order by period_start desc, created_at desc
				limit 200""")
			.param("userId", user.value())
			.param("status", status == null ? null : status.name())
			.param("periodStart", periodStart)
			.query(AlertRepository::map)
			.list();
	}

	public Optional<AlertRow> findById(UserId user, UUID id) {
		return jdbc.sql(COLUMNS + " and id = :id")
			.param("userId", user.value())
			.param("id", id)
			.query(AlertRepository::map)
			.optional();
	}

	public int updateStatus(UserId user, UUID id, AlertStatus status) {
		return jdbc.sql("""
				update anomaly_alerts set status = :status, resolved_at = now(), updated_at = now(), version = version + 1
				where user_id = :userId and id = :id
				""").param("status", status.name()).param("userId", user.value()).param("id", id).update();
	}

	/** Open alerts about a transaction that was deleted or changed; resolved ones are kept as the user left them. */
	public int deleteOpenForTransaction(UserId user, UUID transactionId) {
		return jdbc.sql("""
				delete from anomaly_alerts
				where user_id = :userId and subject_type = 'TRANSACTION' and subject_id = :transactionId and status = 'OPEN'
				""").param("userId", user.value()).param("transactionId", transactionId).update();
	}

	public int deleteAllForDemoReset(UserId user) {
		return jdbc.sql("delete from anomaly_alerts where user_id = :userId").param("userId", user.value()).update();
	}

	private static AlertRow map(ResultSet rs, int i) throws SQLException {
		return new AlertRow(rs.getObject("id", UUID.class), RuleId.valueOf(rs.getString("rule_id")),
				SubjectType.valueOf(rs.getString("subject_type")), rs.getObject("subject_id", UUID.class),
				rs.getObject("period_start", LocalDate.class), Severity.valueOf(rs.getString("severity")),
				rs.getString("explanation"), AlertStatus.valueOf(rs.getString("status")),
				rs.getTimestamp("created_at").toInstant());
	}

}
