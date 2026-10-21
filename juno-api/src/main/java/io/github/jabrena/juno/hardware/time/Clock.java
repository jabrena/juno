package io.github.jabrena.juno.hardware.time;

/**
 * The board's monotonic clock. {@link io.github.jabrena.juno.hardware.Arduino#getClock()} is the board's own timer,
 * which the compiler lowers to the Arduino core calls; a unit test passes {@code mock(Clock.class)} instead. Get it
 * once, at startup, and pass it to the classes that need it.
 */
public interface Clock {

    /** Milliseconds since the board started, wrapping after about 24.8 days. */
    int millis();

    /** Microseconds since the board started, wrapping after about 35.8 minutes. */
    int micros();
}
