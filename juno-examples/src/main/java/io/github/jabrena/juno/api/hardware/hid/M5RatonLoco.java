package io.github.jabrena.juno.api.hardware.hid;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.M5StackCardputer;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.hid.Mouse;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * {@link RatonLoco} for the M5Stack Cardputer: the ESP32-S3 acts as a USB HID mouse through its core's TinyUSB stack
 * and moves the connected computer's pointer around a square, five 50-pixel steps per side. At the same time, a neon
 * mouse with animated ears, eyes, whiskers, tail and star trails travels around the Cardputer's display. The USB port
 * remains the serial console, so each side is reported at 115200 baud and can be checked with {@code juno:monitor}.
 *
 * <p>Build and upload it with:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.hid.M5RatonLoco \
 *   -Djuno.board=m5stack-cardputer
 * ./mvnw -f juno-examples/pom.xml juno:monitor \
 *   -Djuno.board=m5stack-cardputer -Djuno.baudRate=115200
 * }</pre>
 *
 * <p><strong>Warning:</strong> Once uploaded, this program takes control of the pointer on the computer connected to
 * the Cardputer's USB port and continues moving it indefinitely. Disconnect the board or replace the sketch to stop it.
 */
@Board(M5StackCardputerBoard.class)
public final class M5RatonLoco {
    private static final int MOVE_DELAY = 1000;
    private static final int STEP = 50;
    private static final int STEPS_PER_SIDE = 5;
    private static final int DISPLAY_X_STEP = 32;
    private static final int DISPLAY_Y_STEP = 14;
    private static final int START_X = 24;
    private static final int START_Y = 22;
    private static final int BACKGROUND = 0x1082;
    private static final int BODY = 0x07FF;
    private static final int BODY_HIGHLIGHT = 0x5FFF;
    private static final int EAR = 0xF81F;
    private static final int EYE = 0x0000;
    private static final int NOSE = 0xF800;
    private static final int STAR = 0xFFE0;

    private M5RatonLoco() {
    }

    /** Starts serial and the USB mouse, then moves the pointer around a square forever. */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Mouse mouse = M5StackCardputer.getMouse();
        Display display = M5StackCardputer.getDisplay();
        Serial.println("M5 Cardputer booting");
        display.begin();
        Serial.println("M5 display started");
        mouse.begin();
        Serial.println("M5 HID mouse started");
        drawMouse(display, START_X, START_Y, 0);
        Serial.println("M5 animation started");

        int x = START_X;
        int y = START_Y;
        int frame = 0;
        while (true) {
            Serial.println("Right");
            x = moveHorizontal(mouse, display, x, y, DISPLAY_X_STEP, STEP, frame);
            frame = frame + STEPS_PER_SIDE;
            Serial.println("Down");
            y = moveVertical(mouse, display, x, y, DISPLAY_Y_STEP, STEP, frame);
            frame = frame + STEPS_PER_SIDE;
            Serial.println("Left");
            x = moveHorizontal(mouse, display, x, y, -DISPLAY_X_STEP, -STEP, frame);
            frame = frame + STEPS_PER_SIDE;
            Serial.println("Up");
            y = moveVertical(mouse, display, x, y, -DISPLAY_Y_STEP, -STEP, frame);
            frame = frame + STEPS_PER_SIDE;
        }
    }

    private static int moveHorizontal(Mouse mouse, Display display, int x, int y, int displayStep, int hidStep,
                                      int frame) {
        int i = 0;
        while (i < STEPS_PER_SIDE) {
            x = x + displayStep;
            drawMouse(display, x, y, frame);
            mouse.move(hidStep, 0);
            Delay.millis(MOVE_DELAY);
            i = i + 1;
            frame = frame + 1;
        }
        return x;
    }

    private static int moveVertical(Mouse mouse, Display display, int x, int y, int displayStep, int hidStep,
                                    int frame) {
        int i = 0;
        while (i < STEPS_PER_SIDE) {
            y = y + displayStep;
            drawMouse(display, x, y, frame);
            mouse.move(0, hidStep);
            Delay.millis(MOVE_DELAY);
            i = i + 1;
            frame = frame + 1;
        }
        return y;
    }

    /** Draws one complete frame, so the old mouse and its trail are cleared without allocating a bitmap. */
    private static void drawMouse(Display display, int x, int y, int frame) {
        int bob = frame & 1;
        display.fillScreen(BACKGROUND);

        // Star trails move independently of the mouse, creating a small animated night sky.
        display.fillRect((frame * 17) % 224 + 4, 8, 2, 2, STAR);
        display.fillRect((frame * 29) % 224 + 4, 112, 2, 2, STAR);
        display.fillRect((frame * 11) % 220 + 8, 20, 2, 2, STAR);
        display.fillRect((frame * 23) % 216 + 12, 102, 2, 2, STAR);

        int bodyY = y + bob;
        display.fillRect(x + 7, bodyY + 10, 26, 16, BODY);
        display.fillRect(x + 11, bodyY + 6, 18, 22, BODY);
        display.fillRect(x + 14, bodyY + 3, 12, 5, BODY_HIGHLIGHT);
        display.fillRect(x + 5, bodyY, 9, 12, EAR);
        display.fillRect(x + 26, bodyY, 9, 12, EAR);
        display.fillRect(x + 8, bodyY + 3, 4, 5, BODY_HIGHLIGHT);
        display.fillRect(x + 28, bodyY + 3, 4, 5, BODY_HIGHLIGHT);
        display.fillRect(x + 14, bodyY + 13, 4, 4, EYE);
        display.fillRect(x + 24, bodyY + 13, 4, 4, EYE);
        display.fillRect(x + 19, bodyY + 18, 4, 3, NOSE);
        display.fillRect(x - 1, bodyY + 17, 10, 2, BODY_HIGHLIGHT);
        display.fillRect(x + 30, bodyY + 17, 10, 2, BODY_HIGHLIGHT);
        display.fillRect(x - 4, bodyY + 24, 14, 2, BODY_HIGHLIGHT);
        display.fillRect(x + 30, bodyY + 24, 14, 2, BODY_HIGHLIGHT);

        // The tail curls in the opposite direction every other frame.
        if ((frame & 2) == 0) {
            display.fillRect(x + 33, bodyY + 24, 10, 3, EAR);
            display.fillRect(x + 40, bodyY + 20, 3, 7, EAR);
        } else {
            display.fillRect(x - 8, bodyY + 24, 10, 3, EAR);
            display.fillRect(x - 8, bodyY + 20, 3, 7, EAR);
        }

        display.setCursor(8, 118);
        display.setTextColor(BODY_HIGHLIGHT, BACKGROUND);
        display.setTextSize(1);
        display.print("M5 HID // RATON LOCO");
    }
}
