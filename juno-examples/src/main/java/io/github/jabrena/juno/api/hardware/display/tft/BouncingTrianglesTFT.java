package io.github.jabrena.juno.api.hardware.display.tft;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.carrier.PortentaHatCarrier;
import io.github.jabrena.juno.hardware.device.tft.Mhs35TftDisplay;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * The typical first demo for a new screen: three filled triangles bouncing off the edges of an MHS-3.5 display on a
 * Portenta Hat Carrier.
 *
 * <p>Each frame repaints, for every triangle, the box that covers both where it was and where it is now, one row at a
 * time: black outside the triangle and its color inside. Every pixel of that box is written once, so the screen is
 * never blanked between frames and nothing flickers. Every hundredth frame is logged to Serial so a monitor shows the
 * loop is running.
 *
 * <p>Positions, speeds and colors live in arrays allocated once at startup; the loop allocates nothing.
 */
@Board(ArduinoPortentaH7Board.class)
public final class BouncingTrianglesTFT {
    private static final int COUNT = 3;
    /** Half the base and half the height: a triangle spans {@code 2 * SIZE + 1} pixels each way. */
    private static final int SIZE = 36;
    private static final int[] COLORS = {Display.RED, Display.GREEN, Display.CYAN};

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);
        Serial.println("started");

        Mhs35TftDisplay tft = new Mhs35TftDisplay(new PortentaHatCarrier());
        Serial.println("display ready");

        int width = tft.width();
        int height = tft.height();
        tft.fillScreen(Display.BLACK);

        int[] centerX = new int[COUNT];
        int[] centerY = new int[COUNT];
        int[] velocityX = new int[COUNT];
        int[] velocityY = new int[COUNT];
        int[] previousX = new int[COUNT];
        int[] previousY = new int[COUNT];
        for (int i = 0; i < COUNT; i++) {
            centerX[i] = SIZE + 40 + i * 120;
            centerY[i] = SIZE + 30 + i * 70;
            velocityX[i] = 2 + i;
            velocityY[i] = 1 + i;
            previousX[i] = centerX[i];
            previousY[i] = centerY[i];
        }

        int frame = 0;
        while (true) {
            for (int i = 0; i < COUNT; i++) {
                centerX[i] = centerX[i] + velocityX[i];
                centerY[i] = centerY[i] + velocityY[i];
                if (centerX[i] - SIZE < 0 || centerX[i] + SIZE >= width) {
                    velocityX[i] = -velocityX[i];
                    centerX[i] = centerX[i] + 2 * velocityX[i];
                }
                if (centerY[i] - SIZE < 0 || centerY[i] + SIZE >= height) {
                    velocityY[i] = -velocityY[i];
                    centerY[i] = centerY[i] + 2 * velocityY[i];
                }
                repaint(tft, previousX[i], previousY[i], centerX[i], centerY[i], COLORS[i]);
                previousX[i] = centerX[i];
                previousY[i] = centerY[i];
            }
            frame++;
            if (frame % 100 == 0) {
                Serial.print("frame ");
                Serial.println(frame);
            }
        }
    }

    /**
     * Repaints the box covering a triangle's old and new positions: its rows from the top, black outside the
     * triangle at ({@code x}, {@code y}) and {@code color} inside. The triangle points up, so on a row {@code d}
     * pixels below its apex it spans {@code d / 2} pixels either side of its center.
     */
    private static void repaint(Mhs35TftDisplay tft, int oldX, int oldY, int x, int y, int color) {
        int left = Math.min(oldX, x) - SIZE;
        int right = Math.max(oldX, x) + SIZE;
        int top = Math.min(oldY, y) - SIZE;
        int bottom = Math.max(oldY, y) + SIZE;
        int apex = y - SIZE;
        for (int row = top; row <= bottom; row++) {
            int spanLeft = right + 1;
            int spanRight = left - 1;
            if (row >= apex && row <= y + SIZE) {
                int half = (row - apex) / 2;
                spanLeft = x - half;
                spanRight = x + half;
            }
            if (tft.beginPixels(left, row, right - left + 1, 1)) {
                for (int column = left; column <= right; column++) {
                    if (column >= spanLeft && column <= spanRight) {
                        tft.pushPixel(color);
                    } else {
                        tft.pushPixel(Display.BLACK);
                    }
                }
            }
        }
    }
}
