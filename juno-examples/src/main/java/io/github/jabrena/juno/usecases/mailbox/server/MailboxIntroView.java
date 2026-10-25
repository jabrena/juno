package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.image.ArduinoUnoBoardImage;
import io.github.jabrena.juno.util.image.Drawable;
import io.github.jabrena.juno.util.image.JavaLogoImage;

/** The mailbox startup intro: the Java logo, then the Arduino UNO, before the cover is drawn. */
public final class MailboxIntroView {
    /** How long each image stays on screen after it has been drawn. */
    static final int STEP_MILLIS = 3000;

    private final Drawable javaLogo = new JavaLogoImage();
    private final Drawable arduinoBoard = new ArduinoUnoBoardImage();

    public void show() {
        showImage(javaLogo);
        showImage(arduinoBoard);
    }

    private void showImage(Drawable image) {
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        image.draw();
        Delay.millis(STEP_MILLIS);
    }
}
