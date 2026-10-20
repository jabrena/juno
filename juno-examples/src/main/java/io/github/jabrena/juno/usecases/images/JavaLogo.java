package io.github.jabrena.juno.usecases.images;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.miner.intro.JavaLogoImage;
import io.github.jabrena.juno.util.Delay;

/** Continuously redraws the Java logo on the TFT touch shield. */
@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
public final class JavaLogo {
    private final JavaLogoImage image = new JavaLogoImage();

    public static void main() {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(500);
        Serial.println("Java logo example");

        JavaLogo logo = new JavaLogo();
        logo.drawRotated180Degrees();

        Serial.println("Java logo example finished");
    }

    private void draw() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        image.draw();
    }

    private void drawRotated180Degrees() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT);
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        image.draw();
    }
}
