package io.github.jabrena.juno.api.io.gpio;

import io.github.jabrena.juno.api.time.Clock;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only stand-in for the GPIO intrinsics. Every pin reads the same scripted waveform: {@link #highAt} gives the
 * level at a simulated microsecond, and each read advances the {@link Clock} by one microsecond, so protocol code
 * that times edges sees a believable clock. Analog reads return the per-pin {@link #analogValues}.
 */
public final class Gpio {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int INPUT_PULLUP = 2;

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

    /** The level the line shows at a simulated microsecond. Defaults to high (a pulled-up idle line). */
    public static java.util.function.IntPredicate highAt = t -> true;
    /** The value {@link #analogRead} returns for each analog pin, indexed by pin number. */
    public static int[] analogValues = new int[20];
    /** When set, analog reads cycle through these values instead, one per read, whatever the pin. */
    public static int[] analogSequence;
    /** Every {@link #digitalWrite} as {pin, 1 for high or 0 for low}, in order. */
    public static final List<int[]> writes = new ArrayList<>();
    private static int analogReads;
    public static int[] lastPinMode = new int[20];
    public static boolean[] lastOutput = new boolean[20];

    private Gpio() {
    }

    public static void reset() {
        highAt = t -> true;
        analogValues = new int[20];
        analogSequence = null;
        analogReads = 0;
        writes.clear();
        lastPinMode = new int[20];
        lastOutput = new boolean[20];
    }

    public static void pinMode(int pin, int mode) {
        lastPinMode[pin] = mode;
    }

    public static void digitalWrite(int pin, boolean high) {
        lastOutput[pin] = high;
        writes.add(new int[] {pin, high ? 1 : 0});
    }

    public static boolean digitalRead(int pin) {
        boolean high = highAt.test(Clock.now);
        Clock.now++;
        return high;
    }

    public static int analogRead(int pin) {
        if (analogSequence != null) {
            return analogSequence[analogReads++ % analogSequence.length];
        }
        return analogValues[pin];
    }

    public static void analogWrite(int pin, int value) {
    }

    public static void toggle(int pin) {
        lastOutput[pin] = !lastOutput[pin];
    }

    public static int builtinLed() {
        return D13;
    }
}
