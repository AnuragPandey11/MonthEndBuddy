package dev.monthendbuddy.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock tests can move. It starts at the real current time; {@link #advance} moves it forward and {@link #reset}
 * returns to the real time. Clocks derived with {@link #withZone} follow the same instant.
 */
public class MutableClock extends Clock {

	private final AtomicReference<Instant> now;

	private final ZoneId zone;

	public MutableClock() {
		this(new AtomicReference<>(Instant.now()), ZoneOffset.UTC);
	}

	private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
		this.now = now;
		this.zone = zone;
	}

	public void advance(Duration duration) {
		now.updateAndGet(current -> current.plus(duration));
	}

	public void setInstant(Instant instant) {
		now.set(instant);
	}

	public void reset() {
		now.set(Instant.now());
	}

	@Override
	public Instant instant() {
		return now.get();
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(now, zone);
	}

}
