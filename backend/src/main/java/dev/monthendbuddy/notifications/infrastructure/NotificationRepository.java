package dev.monthendbuddy.notifications.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.Uuids;
import dev.monthendbuddy.notifications.NotificationType;
import dev.monthendbuddy.notifications.NotificationView;

/** Notifications, every statement scoped to the user. */
@Repository
public class NotificationRepository {

	private static final String COLUMNS = """
			select id, type, title, body, link, created_at, read_at from notifications where user_id = :userId""";

	private final JdbcClient jdbc;

	public NotificationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Inserts the notification unless one exists for the same source event; returns whether it was inserted. */
	public boolean insertIfNew(UserId user, UUID sourceEventId, NotificationType type, String title, String body,
			String link, Instant at) {
		return jdbc.sql("""
				insert into notifications (id, user_id, type, title, body, link, source_event_id, created_at)
				values (:id, :userId, :type, :title, :body, :link, :sourceEventId, :at)
				on conflict (source_event_id) do nothing
				""")
			.param("id", Uuids.v7())
			.param("userId", user.value())
			.param("type", type.name())
			.param("title", title)
			.param("body", body)
			.param("link", link)
			.param("sourceEventId", sourceEventId)
			.param("at", Timestamp.from(at))
			.update() == 1;
	}

	public List<NotificationView> find(UserId user, boolean unreadOnly, int limit) {
		return jdbc.sql(COLUMNS + """
				 and (not :unreadOnly or read_at is null)
				order by created_at desc, id desc
				limit :limit""")
			.param("userId", user.value())
			.param("unreadOnly", unreadOnly)
			.param("limit", limit)
			.query(NotificationRepository::map)
			.list();
	}

	public Optional<NotificationView> findById(UserId user, UUID id) {
		return jdbc.sql(COLUMNS + " and id = :id")
			.param("userId", user.value())
			.param("id", id)
			.query(NotificationRepository::map)
			.optional();
	}

	/** Stamps {@code read_at} once; reading an already read notification keeps the first time. */
	public int markRead(UserId user, UUID id, Instant at) {
		return jdbc.sql("""
				update notifications set read_at = coalesce(read_at, :at) where user_id = :userId and id = :id
				""").param("at", Timestamp.from(at)).param("userId", user.value()).param("id", id).update();
	}

	public int deleteAllForDemoReset(UserId user) {
		return jdbc.sql("delete from notifications where user_id = :userId").param("userId", user.value()).update();
	}

	private static NotificationView map(ResultSet rs, int i) throws SQLException {
		Timestamp readAt = rs.getTimestamp("read_at");
		return new NotificationView(rs.getObject("id", UUID.class), NotificationType.valueOf(rs.getString("type")),
				rs.getString("title"), rs.getString("body"), rs.getString("link"),
				rs.getTimestamp("created_at").toInstant(), readAt == null ? null : readAt.toInstant());
	}

}
