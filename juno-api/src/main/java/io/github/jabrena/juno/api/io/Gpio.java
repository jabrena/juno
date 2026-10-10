package io.github.jabrena.juno.api.io;

/** GPIO operations recognized as compiler intrinsics by Juno. */
public final class Gpio {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int INPUT_PULLUP = 2;


    // Uno-header pin numbers, identical on the UNO R4 WiFi and the UNO Q (the UNO Q's
    // digital-pin-gpios table lists D0-D13 then A0-A5, matching the R4's PIN_A0 = 14).
    public static final int D0 = 0;
    public static final int D1 = 1;
    public static final int D2 = 2;
    public static final int D3 = 3;
    public static final int D4 = 4;
    public static final int D5 = 5;
    public static final int D6 = 6;
    public static final int D7 = 7;
    public static final int D8 = 8;
    public static final int D9 = 9;
    public static final int D10 = 10;
    public static final int D11 = 11;
    public static final int D12 = 12;
    public static final int D13 = 13;

    public static final int A0 = 14;
    public static final int A1 = 15;
    public static final int A2 = 16;
    public static final int A3 = 17;
    public static final int A4 = 18;
    public static final int A5 = 19;

    private Gpio() {
    }

    public static native void pinMode(int pin, int mode);

    public static native void digitalWrite(int pin, boolean high);

    public static native boolean digitalRead(int pin);

    public static native int analogRead(int pin);

    public static native void analogWrite(int pin, int value);

    public static native void toggle(int pin);

    /** The linked board's onboard LED pin: the UNO R4 WiFi's pin 13, the UNO Q's LED3 red channel. */
    public static native int builtinLed();
}
