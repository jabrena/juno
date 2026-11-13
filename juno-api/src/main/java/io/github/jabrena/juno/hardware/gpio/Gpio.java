package io.github.jabrena.juno.hardware.gpio;

/**
 * The board's GPIO pins. {@link io.github.jabrena.juno.hardware.MCU#getGpio()} exposes the board's own pins, which the compiler lowers to the Arduino core
 * calls; a unit test passes {@code mock(Gpio.class)} instead. Pin names and modes are resolved by the linked board
 * ({@link #digitalPin(String)}, {@link #getOutput()}) and are not raw MCU GPIO numbers. On the Nano ESP32, for
 * example, D13 reaches GPIO48 and A4 reaches GPIO11.
 * Get the board's pins once, at startup, and pass them to the classes that need them.
 */
public interface Gpio {

    void pinMode(int pin, int mode);

    void digitalWrite(int pin, boolean high);

    boolean digitalRead(int pin);

    int analogRead(int pin);

    void analogWrite(int pin, int value);

    void toggle(int pin);

    /** The linked board's onboard LED pin: D13 on the UNO R4 WiFi and Nano ESP32, LED3 red on the UNO Q. */
    int getBuiltinLed();

    /**
     * Resolves a logical digital pin name for this board. The accepted portable names are {@code D0} through
     * {@code D13}; a board may also expose board-specific names such as {@code GPIO42}. Invalid or unavailable names
     * raise {@link IllegalArgumentException} on the board.
     */
    int digitalPin(String name);

    /**
     * Resolves a logical analog pin name for this board. The accepted portable names are {@code A0} through {@code A5};
     * a board may expose additional names. Invalid or unavailable names raise {@link IllegalArgumentException} on the
     * board.
     */
    int analogPin(String name);

    /** The pin mode INPUT, for {@link #pinMode}; the board supplies its value. */
    int getInput();

    /** The pin mode OUTPUT, for {@link #pinMode}; the board supplies its value. */
    int getOutput();

    /** The pin mode INPUT_PULLUP, for {@link #pinMode}; the board supplies its value. */
    int getInputPullup();
}
