package demo;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** The 2.8" TFT touch shield: setup, geometry and the drawing primitives. */
public final class TftTouchShieldApi {
    public static void main(String[] args) {
        TftTouchShield tft = new TftTouchShield();

        tft.setRotation(1);
        int red = TftTouchShield.color(255, 0, 0);
        tft.fillScreen(0);
        tft.fillRect(10, 10, tft.width() / 2, tft.height() / 2, red);
        tft.drawRect(5, 5, 20, 20, red);
        tft.drawCircle(60, 60, 10, red);
        tft.fillCircle(90, 60, 10, red);
        tft.drawHorizontalLine(0, 100, 50, red);
        tft.drawVerticalLine(100, 0, 50, red);
        tft.drawPixel(1, 1, red);
    }
}
