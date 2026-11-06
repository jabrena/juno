package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.time.Clock;

/** A clock that advances a little on every read, so a wait loop in a unit test always ends. */
final class FakeClock implements Clock {
    private int now;

    @Override
    public int millis() {
        now += 10;
        return now;
    }

    @Override
    public int micros() {
        return now * 1000;
    }
}
