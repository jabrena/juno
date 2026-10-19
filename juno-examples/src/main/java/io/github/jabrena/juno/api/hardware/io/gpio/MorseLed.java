package io.github.jabrena.juno.api.hardware.io.gpio;

/**
 * Plays a Morse pattern on one LED, one time unit per {@link #tick()}, and loops it. The pins come in as a
 * {@link Gpio}, so a unit test can hand it {@code mock} instead of the board's.
 */
public final class MorseLed {
    private final Gpio gpio;
    private final int pin;
    private final byte[] pattern;
    private int position;
    private boolean lit;

    /**
     * Sets the pin up as an output and turns the LED off.
     *
     * @param pattern one entry per time unit: 1 for on, 0 for off
     */
    public MorseLed(Gpio gpio, int pin, byte[] pattern) {
        this.gpio = gpio;
        this.pin = pin;
        this.pattern = pattern;
        gpio.pinMode(pin, Gpio.getOutput());
        gpio.digitalWrite(pin, false);
    }

    /** Shows the next time unit, writing the pin only when the LED changes. */
    public void tick() {
        boolean next = pattern[position] != 0;
        if (next != lit) {
            gpio.digitalWrite(pin, next);
            lit = next;
        }
        position = position + 1 == pattern.length ? 0 : position + 1;
    }
}
