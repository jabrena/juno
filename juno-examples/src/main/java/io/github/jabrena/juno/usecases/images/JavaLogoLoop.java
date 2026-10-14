package io.github.jabrena.juno.usecases.images;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.miner2.cover.JavaLogoImage;

/** Continuously redraws the Java logo on the TFT touch shield. */
@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
public final class JavaLogoLoop {
    private static final int WIDTH = 240;
    private static final int HEIGHT = 320;
    private static final int FRAME_MILLIS = 2000;

    private JavaLogoLoop() {
    }

    public static void main() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        while (true) {
            TftTouchShield.fillScreen(TftTouchShield.WHITE);
            JavaLogoImage.draw((WIDTH - JavaLogoImage.WIDTH) / 2, (HEIGHT - JavaLogoImage.HEIGHT) / 2);
            Delay.millis(FRAME_MILLIS);
        }
    }
}
