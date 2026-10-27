package dev.monthendbuddy.common;

import java.util.List;

/** One page of a keyset-paginated list; {@code nextCursor} is {@code null} on the last page (architecture §13.1). */
public record CursorPage<T>(List<T> items, String nextCursor) {

	public CursorPage {
		items = List.copyOf(items);
	}

}
