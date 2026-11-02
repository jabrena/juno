package io.github.jabrena.juno.hardware.device.tft;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.util.Delay;

/**
 * Low-level resistive-touch-panel pin sampling, split out of {@link TftTouchShield} to keep its
 * cyclomatic complexity down. The panel shares its pins with the display bus (see
 * {@link TftTouchShield#readTouch()}), so every sample here temporarily reconfigures them.
 */
final class TftTouch {
    private static final int PIN_TOUCH_YP = 17; // A3, analog
    private static final int PIN_TOUCH_XM = 16; // A2, analog
    private static final int PIN_TOUCH_YM = 9;
    private static final int PIN_TOUCH_XP = 8;

    // The touch pins double as display bus pins, so they can still hold charge from drawing when
    // they switch to analog inputs; a longer settle plus two agreeing samples filters that out.
    private static final int TOUCH_SETTLE_MICROS = 100;

    private final Gpio gpio;
    private final TftBus bus;

    TftTouch(Gpio gpio, TftBus bus) {
        this.gpio = gpio;
        this.bus = bus;
    }

    // Adafruit TouchScreen's approach: X+ to ground, Y- to VCC, then compare the two floating plates.
    int readTouchPressure() {
        gpio.pinMode(PIN_TOUCH_XP, Gpio.getOutput());
        gpio.digitalWrite(PIN_TOUCH_XP, false);
        gpio.pinMode(PIN_TOUCH_YM, Gpio.getOutput());
        gpio.digitalWrite(PIN_TOUCH_YM, true);
        gpio.digitalWrite(PIN_TOUCH_XM, false);
        gpio.pinMode(PIN_TOUCH_XM, Gpio.getInput());
        gpio.digitalWrite(PIN_TOUCH_YP, false);
        gpio.pinMode(PIN_TOUCH_YP, Gpio.getInput());
        Delay.micros(TOUCH_SETTLE_MICROS);
        int z1 = gpio.analogRead(PIN_TOUCH_XM);
        int z2 = gpio.analogRead(PIN_TOUCH_YP);
        return 1023 - (z2 - z1);
    }

    // Drive the X plate end to end and read its voltage through the floating Y plate.
    int readTouchRawX() {
        gpio.pinMode(PIN_TOUCH_YP, Gpio.getInput());
        gpio.pinMode(PIN_TOUCH_YM, Gpio.getInput());
        gpio.digitalWrite(PIN_TOUCH_YM, false);
        gpio.pinMode(PIN_TOUCH_XP, Gpio.getOutput());
        gpio.pinMode(PIN_TOUCH_XM, Gpio.getOutput());
        gpio.digitalWrite(PIN_TOUCH_XP, true);
        gpio.digitalWrite(PIN_TOUCH_XM, false);
        Delay.micros(TOUCH_SETTLE_MICROS);
        return 1023 - gpio.analogRead(PIN_TOUCH_YP);
    }

    // Drive the Y plate end to end and read its voltage through the floating X plate.
    int readTouchRawY() {
        gpio.pinMode(PIN_TOUCH_XP, Gpio.getInput());
        gpio.pinMode(PIN_TOUCH_XM, Gpio.getInput());
        gpio.digitalWrite(PIN_TOUCH_XP, false);
        gpio.pinMode(PIN_TOUCH_YP, Gpio.getOutput());
        gpio.pinMode(PIN_TOUCH_YM, Gpio.getOutput());
        gpio.digitalWrite(PIN_TOUCH_YP, true);
        gpio.digitalWrite(PIN_TOUCH_YM, false);
        Delay.micros(TOUCH_SETTLE_MICROS);
        return 1023 - gpio.analogRead(PIN_TOUCH_XM);
    }

    // The touch pins double as RS, CS, and data bits 0-1; hand them back to the display bus.
    void restoreBusAfterTouch() {
        gpio.pinMode(TftTouchShield.PIN_RS, Gpio.getOutput());
        gpio.pinMode(TftTouchShield.PIN_CS, Gpio.getOutput());
        gpio.digitalWrite(TftTouchShield.PIN_RS, true);
        gpio.digitalWrite(TftTouchShield.PIN_CS, false);
        gpio.digitalWrite(TftTouchShield.PIN_WR, true);
        bus.restoreDataPins();
    }

    static int map(int value, int fromLow, int fromHigh, int toLow, int toHigh) {
        return (value - fromLow) * (toHigh - toLow) / (fromHigh - fromLow) + toLow;
    }

    static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }
}
