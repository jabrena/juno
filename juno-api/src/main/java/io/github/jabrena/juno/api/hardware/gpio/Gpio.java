package io.github.jabrena.juno.api.hardware.gpio;

/**
 * The board's GPIO pins. {@link io.github.jabrena.juno.api.hardware.MCU#getGpio()} is the board's own pins, which the compiler lowers to the Arduino core
 * calls; a unit test passes {@code mock(Gpio.class)} instead. Pin numbers and modes come from the static getters
 * ({@link #getD13()}, {@link #getOutput()}): the Uno-header numbering, identical on the UNO R4 WiFi and the UNO Q.
 * Get the board's pins once, at startup, and pass them to the classes that need them.
 */
public interface Gpio {

    void pinMode(int pin, int mode);

    void digitalWrite(int pin, boolean high);

    boolean digitalRead(int pin);

    int analogRead(int pin);

    void analogWrite(int pin, int value);

    void toggle(int pin);

    /** The linked board's onboard LED pin: the UNO R4 WiFi's pin 13, the UNO Q's LED3 red channel. */
    int getBuiltinLed();

    /** The pin number of D0, usable wherever a pin is expected. */
    static int getD0() {
        return 0;
    }

    /** The pin number of D1, usable wherever a pin is expected. */
    static int getD1() {
        return 1;
    }

    /** The pin number of D2, usable wherever a pin is expected. */
    static int getD2() {
        return 2;
    }

    /** The pin number of D3, usable wherever a pin is expected. */
    static int getD3() {
        return 3;
    }

    /** The pin number of D4, usable wherever a pin is expected. */
    static int getD4() {
        return 4;
    }

    /** The pin number of D5, usable wherever a pin is expected. */
    static int getD5() {
        return 5;
    }

    /** The pin number of D6, usable wherever a pin is expected. */
    static int getD6() {
        return 6;
    }

    /** The pin number of D7, usable wherever a pin is expected. */
    static int getD7() {
        return 7;
    }

    /** The pin number of D8, usable wherever a pin is expected. */
    static int getD8() {
        return 8;
    }

    /** The pin number of D9, usable wherever a pin is expected. */
    static int getD9() {
        return 9;
    }

    /** The pin number of D10, usable wherever a pin is expected. */
    static int getD10() {
        return 10;
    }

    /** The pin number of D11, usable wherever a pin is expected. */
    static int getD11() {
        return 11;
    }

    /** The pin number of D12, usable wherever a pin is expected. */
    static int getD12() {
        return 12;
    }

    /** The pin number of D13, usable wherever a pin is expected. */
    static int getD13() {
        return 13;
    }

    /** The pin number of A0, usable wherever a pin is expected. */
    static int getA0() {
        return 14;
    }

    /** The pin number of A1, usable wherever a pin is expected. */
    static int getA1() {
        return 15;
    }

    /** The pin number of A2, usable wherever a pin is expected. */
    static int getA2() {
        return 16;
    }

    /** The pin number of A3, usable wherever a pin is expected. */
    static int getA3() {
        return 17;
    }

    /** The pin number of A4, usable wherever a pin is expected. */
    static int getA4() {
        return 18;
    }

    /** The pin number of A5, usable wherever a pin is expected. */
    static int getA5() {
        return 19;
    }

    /** The pin mode INPUT, for {@link #pinMode}. */
    static int getInput() {
        return 0;
    }

    /** The pin mode OUTPUT, for {@link #pinMode}. */
    static int getOutput() {
        return 1;
    }

    /** The pin mode INPUT_PULLUP, for {@link #pinMode}. */
    static int getInputPullup() {
        return 2;
    }
}
