package demo;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The 2.8" TFT touch shield: setup, geometry and the drawing primitives. */
public final class TftTouchShieldApi {
    public static void main(String[] args) {
        TftTouchShield.begin();
        
        TftTouchShield.setRotation(1);
        int red = TftTouchShield.color(255, 0, 0);
        TftTouchShield.fillScreen(0);
        TftTouchShield.fillRect(10, 10, TftTouchShield.width() / 2, TftTouchShield.height() / 2, red);
        TftTouchShield.drawRect(5, 5, 20, 20, red);
        TftTouchShield.drawCircle(60, 60, 10, red);
        TftTouchShield.fillCircle(90, 60, 10, red);
        TftTouchShield.drawHorizontalLine(0, 100, 50, red);
        TftTouchShield.drawVerticalLine(100, 0, 50, red);
        TftTouchShield.drawPixel(1, 1, red);
    }
}
