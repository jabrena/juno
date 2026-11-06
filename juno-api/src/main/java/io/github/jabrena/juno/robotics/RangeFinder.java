package io.github.jabrena.juno.robotics;

/**
 * A sensor that reports the distance to the nearest object.
 *
 * <p>The returned range uses Juno's standard length unit, metres. {@link Float#NaN} means that the sensor did not
 * receive a valid echo.
 */
public interface RangeFinder {

    /** Returns the range in metres, or {@link Float#NaN} when no valid range was measured. */
    float getRange();
}
