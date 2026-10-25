package io.github.jabrena.juno.testing;

import io.github.jabrena.juno.hardware.time.Clock;

/** A deterministic clock whose millisecond reading advances by a fixed amount on every read. */
public final class FrameClock implements Clock {
    private final int step;
    private int nowMillis;

    public FrameClock() {
        this(10);
    }

    public FrameClock(int step) {
        this.step = step;
    }

    @Override
    public int millis() {
        nowMillis += step;
        return nowMillis;
    }

    @Override
    public int micros() {
        return nowMillis * 1_000;
    }

    public void advance(int milliseconds) {
        nowMillis += milliseconds;
    }
}
