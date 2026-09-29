package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.DigitalOutput;
import io.github.jabrena.juno.api.io.hid.Mouse;
import io.github.jabrena.juno.api.io.hid.MouseIcon;
import io.github.jabrena.juno.api.led.LedMatrix;

/**
 * A remote-controlled version of {@link io.github.jabrena.juno.api.io.hid.RatonLoco}: the same
 * built-in-LED blink and USB mouse square-walking behavior, but gated by an On/Off switch drawn on
 * the ELEGOO 2.8" TFT touch screen shield instead of running unconditionally from power-up. The
 * program starts with the switch Off, so the pointer stays put until the user taps the switch's
 * "On" half; tapping "Off" again pauses the walk in place, and tapping "On" resumes it.
 *
 * <p>UNO Q only: the Arduino {@code Mouse} library needs {@code HID.h}, which the UNO Q's Zephyr
 * Arduino core does not provide, so this example cannot build for {@link
 * io.github.jabrena.juno.annotations.ArduinoUnoQ} — confirmed with a real {@code arduino-cli
 * compile --fqbn arduino:zephyr:unoq}, which fails with {@code HID.h: No such file or directory}
 * from {@code Mouse.h}. USB HID mouse control stays UNO R4 WiFi-only until the UNO Q core gains
 * that header.
 *
 * <p>The example requires the Arduino {@code Mouse} library (see {@code RatonLoco}'s javadoc) and
 * the {@link TftTouchShield} wiring.
 *
 * <p><strong>Warning:</strong> while the switch is On, this program takes control of the pointer
 * on the computer connected to the board's USB port. Tap the switch's "Off" half, disconnect the
 * board, or replace the sketch to stop it.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class RatonLocoTFT {
    private static final int LED = 13;
    private static final int MOVE_DELAY = 1000;
    private static final int STEP = 50;
    private static final int STEPS_PER_SIDE = 5;
    private static final int POLL_DELAY = 50;

    private static final int SWITCH_X = 40;
    private static final int SWITCH_Y = 40;
    private static final int SWITCH_WIDTH = 160;
    private static final int SWITCH_HEIGHT = 60;
    private static final int SWITCH_HALF = SWITCH_WIDTH / 2;
    private static final int LABEL_Y_OFFSET = 22;
    private static final int OFF_LABEL_X_OFFSET = 22;
    private static final int ON_LABEL_X_OFFSET = 28;

    private static boolean running;

    /**
     * Initializes the USB mouse, LED matrix, and TFT touch shield, displays the mouse icon and an
     * Off switch, then loops forever: polling the switch and, while it is On, blinking the LED and
     * walking the pointer around a square exactly as {@code RatonLoco} does.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(LED);
        Mouse.begin();

        LedMatrix.begin();
        MouseIcon.draw();

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        running = false;
        drawSwitch();

        while (true) {
            pollTouch();
            if (!running) {
                Delay.millis(POLL_DELAY);
                continue;
            }

            led.high();
            if (!pausableDelay(100)) {
                continue;
            }
            led.low();
            if (!pausableDelay(100)) {
                continue;
            }

            if (!walkSide(STEP, 0)) { // Right
                continue;
            }
            if (!walkSide(0, STEP)) { // Down
                continue;
            }
            if (!walkSide(-STEP, 0)) { // Left
                continue;
            }
            if (!walkSide(0, -STEP)) { // Up
                continue;
            }
        }
    }

    /** Moves the pointer {@link #STEPS_PER_SIDE} times by ({@code dx}, {@code dy}), pausable between steps. */
    private static boolean walkSide(int dx, int dy) {
        int i = 0;
        while (i < STEPS_PER_SIDE) {
            if (!pausableDelay(MOVE_DELAY)) {
                return false;
            }
            Mouse.move(dx, dy);
            i = i + 1;
        }
        return true;
    }

    /**
     * Sleeps for {@code totalMs}, polling the switch every {@link #POLL_DELAY} so a tap on "Off"
     * takes effect promptly. Returns {@code false} as soon as the switch is turned Off, leaving the
     * remaining time unslept.
     */
    private static boolean pausableDelay(int totalMs) {
        int elapsed = 0;
        while (elapsed < totalMs) {
            int chunk = totalMs - elapsed;
            if (chunk > POLL_DELAY) {
                chunk = POLL_DELAY;
            }
            Delay.millis(chunk);
            elapsed = elapsed + chunk;
            pollTouch();
            if (!running) {
                return false;
            }
        }
        return true;
    }

    /** Samples the touch panel once and, on a tap inside the switch, updates {@link #running} and redraws it. */
    private static void pollTouch() {
        if (!TftTouchShield.readTouch()) {
            return;
        }
        int x = TftTouchShield.touchX();
        int y = TftTouchShield.touchY();
        boolean insideSwitch = x >= SWITCH_X && x < SWITCH_X + SWITCH_WIDTH
                && y >= SWITCH_Y && y < SWITCH_Y + SWITCH_HEIGHT;
        if (!insideSwitch) {
            return;
        }
        boolean requestedRunning = x >= SWITCH_X + SWITCH_HALF;
        if (requestedRunning != running) {
            running = requestedRunning;
            drawSwitch();
        }
    }

    /** Draws the switch, highlighting whichever half ("Off" or "On") matches {@link #running}. */
    private static void drawSwitch() {
        int offColor = TftTouchShield.GRAY;
        int onColor = TftTouchShield.GRAY;
        if (running) {
            onColor = TftTouchShield.GREEN;
        } else {
            offColor = TftTouchShield.RED;
        }

        TftTouchShield.fillRect(SWITCH_X, SWITCH_Y, SWITCH_HALF, SWITCH_HEIGHT, offColor);
        TftTouchShield.fillRect(SWITCH_X + SWITCH_HALF, SWITCH_Y, SWITCH_HALF, SWITCH_HEIGHT, onColor);
        TftTouchShield.drawRect(SWITCH_X, SWITCH_Y, SWITCH_WIDTH, SWITCH_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.drawVerticalLine(SWITCH_X + SWITCH_HALF, SWITCH_Y, SWITCH_HEIGHT, TftTouchShield.WHITE);

        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, offColor);
        TftTouchShield.setCursor(SWITCH_X + OFF_LABEL_X_OFFSET, SWITCH_Y + LABEL_Y_OFFSET);
        TftTouchShield.print("Off");
        TftTouchShield.setTextColor(TftTouchShield.WHITE, onColor);
        TftTouchShield.setCursor(SWITCH_X + SWITCH_HALF + ON_LABEL_X_OFFSET, SWITCH_Y + LABEL_Y_OFFSET);
        TftTouchShield.print("On");
    }
}
