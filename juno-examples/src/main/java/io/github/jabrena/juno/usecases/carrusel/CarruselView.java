package io.github.jabrena.juno.usecases.carrusel;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.image.ArduinoNanoEsp32Image;
import io.github.jabrena.juno.util.image.ArduinoUnoBoardImage;
import io.github.jabrena.juno.util.image.Drawable;
import io.github.jabrena.juno.util.image.JavaLogoImage;
import io.github.jabrena.juno.util.image.M5StackCardputerImage;
import io.github.jabrena.juno.util.mvc.View;

/** The carousel view: Java logo, Arduino UNO, Nano ESP32, and Cardputer, three seconds each. */
public final class CarruselView implements View {
    static final int STEP_MILLIS = 3000;
    static final int NONE = -1;

    private final Clock clock;
    private final Drawable[] images = {
        new JavaLogoImage(),
        new ArduinoUnoBoardImage(),
        new ArduinoNanoEsp32Image(),
        new M5StackCardputerImage()
    };
    private int shownStep;
    private int shownAt;

    public CarruselView(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void enter() {
        shownStep = NONE;
    }

    @Override
    public void render() {
        int now = clock.millis();
        if (shownStep != NONE && now - shownAt < STEP_MILLIS) {
            return;
        }
        shownStep = shownStep == NONE || shownStep == images.length - 1 ? 0 : shownStep + 1;
        drawFull(images[shownStep]);
        shownAt = now;
    }

    private void drawFull(Drawable image) {
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        image.draw();
    }

    @Override
    public void touch(int x, int y) {
    }
}
