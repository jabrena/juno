package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.Gpio;

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

    private TftTouch() {
    }

    // Adafruit TouchScreen's approach: X+ to ground, Y- to VCC, then compare the two floating plates.
    static int readTouchPressure() {
        Gpio.pinMode(PIN_TOUCH_XP, Gpio.OUTPUT);
        Gpio.digitalWrite(PIN_TOUCH_XP, false);
        Gpio.pinMode(PIN_TOUCH_YM, Gpio.OUTPUT);
        Gpio.digitalWrite(PIN_TOUCH_YM, true);
        Gpio.digitalWrite(PIN_TOUCH_XM, false);
        Gpio.pinMode(PIN_TOUCH_XM, Gpio.INPUT);
        Gpio.digitalWrite(PIN_TOUCH_YP, false);
        Gpio.pinMode(PIN_TOUCH_YP, Gpio.INPUT);
        Delay.micros(TOUCH_SETTLE_MICROS);
        int z1 = Gpio.analogRead(PIN_TOUCH_XM);
        int z2 = Gpio.analogRead(PIN_TOUCH_YP);
        return 1023 - (z2 - z1);
    }

    // Drive the X plate end to end and read its voltage through the floating Y plate.
    static int readTouchRawX() {
        Gpio.pinMode(PIN_TOUCH_YP, Gpio.INPUT);
        Gpio.pinMode(PIN_TOUCH_YM, Gpio.INPUT);
        Gpio.digitalWrite(PIN_TOUCH_YM, false);
        Gpio.pinMode(PIN_TOUCH_XP, Gpio.OUTPUT);
        Gpio.pinMode(PIN_TOUCH_XM, Gpio.OUTPUT);
        Gpio.digitalWrite(PIN_TOUCH_XP, true);
        Gpio.digitalWrite(PIN_TOUCH_XM, false);
        Delay.micros(TOUCH_SETTLE_MICROS);
        return 1023 - Gpio.analogRead(PIN_TOUCH_YP);
    }

    // Drive the Y plate end to end and read its voltage through the floating X plate.
    static int readTouchRawY() {
        Gpio.pinMode(PIN_TOUCH_XP, Gpio.INPUT);
        Gpio.pinMode(PIN_TOUCH_XM, Gpio.INPUT);
        Gpio.digitalWrite(PIN_TOUCH_XP, false);
        Gpio.pinMode(PIN_TOUCH_YP, Gpio.OUTPUT);
        Gpio.pinMode(PIN_TOUCH_YM, Gpio.OUTPUT);
        Gpio.digitalWrite(PIN_TOUCH_YP, true);
        Gpio.digitalWrite(PIN_TOUCH_YM, false);
        Delay.micros(TOUCH_SETTLE_MICROS);
        return 1023 - Gpio.analogRead(PIN_TOUCH_XM);
    }

    // The touch pins double as RS, CS, and data bits 0-1; hand them back to the display bus.
    static void restoreBusAfterTouch() {
        Gpio.pinMode(TftTouchShield.PIN_RS, Gpio.OUTPUT);
        Gpio.pinMode(TftTouchShield.PIN_CS, Gpio.OUTPUT);
        Gpio.digitalWrite(TftTouchShield.PIN_RS, true);
        Gpio.digitalWrite(TftTouchShield.PIN_CS, false);
        Gpio.digitalWrite(TftTouchShield.PIN_WR, true);
        TftBus.restoreDataPins();
    }

    static int map(int value, int fromLow, int fromHigh, int toLow, int toHigh) {
        return (value - fromLow) * (toHigh - toLow) / (fromHigh - fromLow) + toLow;
    }

    static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }
}
