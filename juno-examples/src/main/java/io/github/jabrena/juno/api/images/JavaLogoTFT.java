package io.github.jabrena.juno.api.images;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.image.Drawable;
import io.github.jabrena.juno.util.image.JavaLogoImage;

/**
 * Shows the Java logo centered on the ELEGOO 2.8" TFT touch screen shield. The image is a palette of RGB565 colors
 * and run-length rows ({@link JavaLogoImage}), so it is drawn once from flash onto a white screen and nothing is
 * allocated afterwards.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.images.JavaLogoTFT -Djuno.board=arduino-uno-r4-wifi
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-uno-r4-wifi -Djuno.baudRate=115200
 * }</pre>
 */
public final class JavaLogoTFT {
    private static final Drawable LOGO = new JavaLogoImage();

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        LOGO.draw();
        Serial.println("Java logo drawn");

        while (true) {
            Delay.millis(1000);
        }
    }
}
