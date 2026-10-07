package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.DigitalOutput;
import io.github.jabrena.juno.api.hid.Mouse;
import io.github.jabrena.juno.api.hid.MouseIcon;
import io.github.jabrena.juno.api.led.LedMatrix;

/**
 * A remote-controlled version of {@link io.github.jabrena.juno.api.hid.RatonLoco}: the same
 * built-in-LED blink and USB mouse square-walking behavior, but gated by an On/Off switch drawn on
 * the ELEGOO 2.8" TFT touch screen shield instead of running unconditionally from power-up. The
 * whole display is the switch: it starts Off (a red screen), a tap anywhere turns it On (a green
 * screen) and starts the walk, and another tap anywhere pauses the walk in place.
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
 * on the computer connected to the board's USB port. Tap the screen to turn it Off, disconnect the
 * board, or replace the sketch to stop it.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class RatonLocoTFT {
    private static final int LED = 13;
    private static final int MOVE_DELAY = 1000;
    private static final int STEP = 50;
    private static final int STEPS_PER_SIDE = 5;
    private static final int POLL_DELAY = 50;

    private static final int TITLE_SIZE = 6;
    private static final int HINT_SIZE = 2;
    private static final int OFF_TITLE_X = 106;
    private static final int ON_TITLE_X = 124;
    private static final int TITLE_Y = 80;
    private static final int OFF_HINT_X = 76;
    private static final int ON_HINT_X = 70;
    private static final int HINT_Y = 170;

    private static boolean running;

    /**
     * Initializes the USB mouse, LED matrix, and TFT touch shield, displays the mouse icon and a
     * full-screen Off switch, then loops forever: polling the switch and, while it is On, blinking the LED and
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

    /**
     * Samples the touch panel once and, on a tap anywhere on the screen, flips {@link #running}, redraws the
     * switch and waits for the finger to lift so one press toggles exactly once.
     */
    private static void pollTouch() {
        if (!TftTouchShield.readTouch()) {
            return;
        }
        running = !running;
        drawSwitch();
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }

    /** Fills the whole display red ("OFF") or green ("ON") according to {@link #running}. */
    private static void drawSwitch() {
        int background = TftTouchShield.RED;
        String title = "OFF";
        String hint = "Tap to turn on";
        int titleX = OFF_TITLE_X;
        int hintX = OFF_HINT_X;
        if (running) {
            background = TftTouchShield.GREEN;
            title = "ON";
            hint = "Tap to turn off";
            titleX = ON_TITLE_X;
            hintX = ON_HINT_X;
        }

        TftTouchShield.fillScreen(background);
        TftTouchShield.setTextSize(TITLE_SIZE);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, background);
        TftTouchShield.setCursor(titleX, TITLE_Y);
        TftTouchShield.print(title);
        TftTouchShield.setTextSize(HINT_SIZE);
        TftTouchShield.setCursor(hintX, HINT_Y);
        TftTouchShield.print(hint);
    }
}
