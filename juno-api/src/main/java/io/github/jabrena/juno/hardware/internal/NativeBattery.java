package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.power.Battery;

/** The M5Stack Cardputer's battery, implemented by compiler intrinsics over M5Cardputer.Power. */
public final class NativeBattery implements Battery {
    public static final NativeBattery INSTANCE = new NativeBattery();

    private static Battery battery = INSTANCE;

    private NativeBattery() {
    }

    /** The battery device the M5StackCardputer facade hands out. */
    public static Battery battery() {
        return battery;
    }

    /** Replaces the device for a whole-program emulator in a test, never on the board. */
    public static void setBattery(Battery replacement) {
        battery = replacement;
    }

    @Override
    public native int levelPercent();

    @Override
    public native int voltageMillivolts();
}
