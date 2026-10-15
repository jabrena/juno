package io.github.jabrena.juno.usecases.miner2.intro;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.mvc.Navigator;
import io.github.jabrena.juno.api.mvc.View;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2Views;

/**
 * Miner 2's startup intro, shown once: the Java logo for {@link #STEP_MILLIS}, then an Arduino board turned on its side
 * for as long, then it hands over to the cover. Returning to the cover from the chain never shows it again.
 */
public final class IntroView implements View {
    /** How long each image stays on screen after it has been drawn, so slow drawing does not eat into it. */
    static final int STEP_MILLIS = 3000;
    static final int NONE = -1;
    static final int LOGO = 0;
    static final int BOARD = 1;

    private final Navigator navigator;
    private final Drawable javaLogo = new JavaLogoImage();
    private final Drawable arduinoBoard = new ArduinoBoardImage();
    private int shownStep;
    private int shownAt;

    public IntroView(Navigator navigator) {
        this.navigator = navigator;
    }

    @Override
    public void enter() {
        shownStep = NONE;
    }

    @Override
    public void render() {
        if (shownStep != NONE && Clock.millis() - shownAt < STEP_MILLIS) {
            return;
        }
        if (shownStep == BOARD) {
            navigator.show(Miner2Views.COVER);
            return;
        }
        shownStep = shownStep + 1;
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        if (shownStep == LOGO) {
            javaLogo.draw();
        } else {
            arduinoBoard.draw();
        }
        shownAt = Clock.millis();
    }

    @Override
    public void touch(int x, int y) {
    }
}
