package io.github.jabrena.juno.api.hardware.hid;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.BoardModel;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.PortentaHatCarrier;
import io.github.jabrena.juno.hardware.device.tft.Mhs35TftDisplay;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.hid.Mouse;
import io.github.jabrena.juno.hardware.touch.TouchScreen;
import io.github.jabrena.juno.util.Delay;

/**
 * A remote-controlled version of {@link RatonLoco}: the same built-in-LED blink and USB mouse square-walking behavior,
 * but gated by an On/Off switch drawn on a touch screen instead of running unconditionally from power-up. The whole
 * display is the switch: it starts Off (a red screen), a tap anywhere turns it On (a green screen) and starts the walk,
 * and another tap anywhere pauses the walk in place.
 *
 * <p>The touch screen is the board's own:
 * <ul>
 *   <li>UNO R4 WiFi: the ELEGOO 2.8" {@link TftTouchShield}, which also shows the {@link MouseIcon mouse face} on the
 *       LED matrix. It requires the Arduino {@code Mouse} library (see {@code RatonLoco}'s javadoc).</li>
 *   <li>Portenta H7: the MHS-3.5 {@link Mhs35TftDisplay} on the {@link PortentaHatCarrier}.</li>
 * </ul>
 *
 * <p><strong>Warning:</strong> while the switch is On, this program takes control of the pointer on the computer
 * connected to the board's USB port. Tap the screen to turn it Off, disconnect the board, or replace the sketch to stop
 * it.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoPortentaH7Board.class})
public final class RatonLocoTFT {
    private static final int BLINK_DELAY = 100;
    private static final int MOVE_DELAY = 1000;
    private static final int STEP = 50;
    private static final int STEPS_PER_SIDE = 5;
    private static final int POLL_DELAY = 50;
    private static final int RELEASE_POLLS = 3;

    private static final int GLYPH_WIDTH = 6;
    private static final int TITLE_SIZE = 6;
    private static final int HINT_SIZE = 2;
    private static final int HINT_BELOW_TITLE = 90;

    private static Display screen;
    private static TouchScreen touch;
    private static boolean running;

    /**
     * Initializes the USB mouse and the board's touch screen, displays a full-screen Off switch (and, on the UNO R4
     * WiFi, the mouse icon on the LED matrix), then loops forever: polling the switch and, while it is On, blinking the
     * LED and walking the pointer around a square exactly as {@code RatonLoco} does.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Gpio gpio = MCU.getGpio();
        DigitalOutput led = DigitalOutput.of(gpio.getBuiltinLed());
        Mouse mouse = MCU.getMouse();
        openScreen(gpio);

        if (MCU.getBoardModel() == BoardModel.UNO_R4_WIFI) {
            MouseIcon.draw();
        }
        drawSwitch();

        while (true) {
            pollTouch();
            if (!running) {
                Delay.millis(POLL_DELAY);
                continue;
            }
            mouseMovement(mouse, led);
        }
    }

    /**
     * The UNO R4 WiFi's ELEGOO shield in landscape, or the Portenta's MHS-3.5 on the Hat Carrier, which starts in
     * landscape. Both are composed from the board's own {@code gpio}, like the carrier itself.
     */
    private static void openScreen(Gpio gpio) {
        switch (MCU.getBoardModel()) {
            case PORTENTA_H7 -> {
                Mhs35TftDisplay mhs35 = new Mhs35TftDisplay(new PortentaHatCarrier(gpio));
                screen = mhs35;
                touch = mhs35;
            }
            default -> {
                TftTouchShield elegoo = new TftTouchShield(gpio);
                elegoo.setRotation(TftTouchShield.LANDSCAPE);
                screen = elegoo;
                touch = elegoo;
            }
        }
    }

    /** Flashes the built-in LED once; it is left off even when a tap pauses the flash. */
    private static boolean blink(DigitalOutput led) {
        led.high();
        boolean on = pausableDelay(BLINK_DELAY);
        led.low();
        return on && pausableDelay(BLINK_DELAY);
    }

    /** Walks the pointer once around the square: right, down, left and up, stopping as soon as a tap turns it Off. */
    private static boolean mouseMovement(Mouse mouse, DigitalOutput led) {
        return moveSide(mouse, led, STEP, 0)
                && moveSide(mouse, led, 0, STEP)
                && moveSide(mouse, led, -STEP, 0)
                && moveSide(mouse, led, 0, -STEP);
    }

    /**
     * Moves the pointer {@link #STEPS_PER_SIDE} times by ({@code x}, {@code y}), one step a second with an LED flash
     * before each, pausable throughout.
     */
    private static boolean moveSide(Mouse mouse, DigitalOutput led, int x, int y) {
        for (int step = 0; step < STEPS_PER_SIDE; step++) {
            if (!blink(led) || !pausableDelay(MOVE_DELAY - 2 * BLINK_DELAY)) {
                return false;
            }
            mouse.move(x, y);
        }
        return true;
    }

    /**
     * Sleeps for {@code totalMs}, polling the switch every {@link #POLL_DELAY} so a tap takes effect promptly. Returns
     * {@code false} as soon as the switch is turned Off, leaving the remaining time unslept.
     */
    private static boolean pausableDelay(int totalMs) {
        for (int elapsed = 0; elapsed < totalMs; elapsed = elapsed + POLL_DELAY) {
            Delay.millis(Math.min(POLL_DELAY, totalMs - elapsed));
            pollTouch();
            if (!running) {
                return false;
            }
        }
        return true;
    }

    /**
     * Samples the touch panel once and, on a tap anywhere on the screen, flips {@link #running}, redraws the switch and
     * waits for the finger to lift so one press toggles exactly once.
     */
    private static void pollTouch() {
        if (!touch.readTouch()) {
            return;
        }
        running = !running;
        drawSwitch();
        for (int misses = 0; misses < RELEASE_POLLS; misses = touch.readTouch() ? 0 : misses + 1) {
            Delay.millis(10);
        }
    }

    /** Fills the whole display red ("OFF") or green ("ON") according to {@link #running}, text centered. */
    private static void drawSwitch() {
        int background = running ? Display.GREEN : Display.RED;
        int titleY = screen.height() / 3;
        screen.fillScreen(background);
        screen.setTextColor(Display.WHITE, background);
        printCentered(running ? "ON" : "OFF", TITLE_SIZE, titleY);
        printCentered(running ? "Tap to turn off" : "Tap to turn on", HINT_SIZE, titleY + HINT_BELOW_TITLE);
    }

    private static void printCentered(String text, int size, int y) {
        screen.setTextSize(size);
        screen.setCursor((screen.width() - text.length() * GLYPH_WIDTH * size) / 2, y);
        screen.print(text);
    }
}
