package io.github.jabrena.juno.usecases.supabase.dashboard.intro;

import io.github.jabrena.juno.api.hardware.MCU;

import io.github.jabrena.juno.api.mvc.Navigator;
import io.github.jabrena.juno.api.mvc.View;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.api.time.Clock;

/** Dashboard-owned startup intro: Java, then Arduino, before handing control to the gathering view. */
public final class IntroView implements View {
    private static final Clock CLOCK = MCU.getClock();
    /** How long each image stays on screen after it has been drawn, so slow drawing does not eat into it. */
    static final int STEP_MILLIS = 3000;
    static final int NONE = -1;
    static final int LOGO = 0;
    static final int BOARD = 1;

    private final Navigator navigator;
    private final int nextViewId;
    private final Drawable javaLogo = new JavaLogoImage();
    private final Drawable arduinoBoard = new ArduinoBoardImage();
    private int shownStep;
    private int shownAt;

    public IntroView(Navigator navigator, int nextViewId) {
        this.navigator = navigator;
        this.nextViewId = nextViewId;
    }

    @Override
    public void enter() {
        shownStep = NONE;
    }

    @Override
    public void render() {
        if (shownStep != NONE && CLOCK.millis() - shownAt < STEP_MILLIS) {
            return;
        }
        if (shownStep == BOARD) {
            navigator.show(nextViewId);
            return;
        }
        shownStep = shownStep + 1;
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        if (shownStep == LOGO) {
            javaLogo.draw();
        } else {
            arduinoBoard.draw();
        }
        shownAt = CLOCK.millis();
    }

    @Override
    public void touch(int x, int y) {
    }
}
