package io.github.jabrena.juno.usecases.mailbox.server.intro;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.image.ArduinoUnoBoardImage;
import io.github.jabrena.juno.util.image.Drawable;
import io.github.jabrena.juno.util.image.JavaLogoImage;

/** The mailbox startup intro: the Java logo, then the Arduino UNO, before the cover is drawn. */
public final class MailboxIntroView {
    /** How long each image stays on screen after it has been drawn. */
    static final int STEP_MILLIS = 3000;

    private final Drawable javaLogo;
    private final Drawable arduinoBoard;

    /** Builds both images on {@code tft}, the program's one shield instance. */
    public MailboxIntroView(TftTouchShield tft) {
        javaLogo = new JavaLogoImage(tft);
        arduinoBoard = new ArduinoUnoBoardImage(tft);
    }

    public void show(TftTouchShield tft) {
        showImage(tft, javaLogo);
        showImage(tft, arduinoBoard);
    }

    private void showImage(TftTouchShield tft, Drawable image) {
        tft.fillScreen(TftTouchShield.WHITE);
        image.draw();
        Delay.millis(STEP_MILLIS);
    }
}
