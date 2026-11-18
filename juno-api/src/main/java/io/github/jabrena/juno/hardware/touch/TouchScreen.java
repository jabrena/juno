package io.github.jabrena.juno.hardware.touch;

/**
 * A touch input. {@link #readTouch()} samples the panel and, when it is pressed, updates {@link #touchX()} and
 * {@link #touchY()}. The coordinates are in the same frame as {@link io.github.jabrena.juno.hardware.display.Display#width()}
 * and {@link io.github.jabrena.juno.hardware.display.Display#height()} for the display's current rotation, so a control
 * drawn on a display can be hit-tested without conversion. {@code ViewRouter} depends on this interface, not on a device.
 */
public interface TouchScreen {
    /** Samples the panel and returns whether it is currently pressed. */
    boolean readTouch();

    /** The X coordinate of the last successful {@link #readTouch()}. */
    int touchX();

    /** The Y coordinate of the last successful {@link #readTouch()}. */
    int touchY();
}
