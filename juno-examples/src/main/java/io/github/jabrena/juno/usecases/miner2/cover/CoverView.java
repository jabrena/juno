package io.github.jabrena.juno.usecases.miner2.cover;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.mvc.Navigator;
import io.github.jabrena.juno.api.mvc.View;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.games.blockchain.GameScreen;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2Views;

/**
 * Miner 2's cover, in three steps: the Java logo for {@link #STEP_MILLIS}, then an Arduino board turned on its side
 * for as long, then the MINER title with a PLAY button that opens the chain. The node is already mining behind it.
 */
public final class CoverView implements View {
    static final int STEP_MILLIS = 3000;
    static final int LOGO = 0;
    static final int BOARD = 1;
    static final int TITLE = 2;

    private static final int WIDTH = 240;
    private static final int HEIGHT = 320;
    private static final int TITLE_SIZE = 6;
    private static final int TITLE_Y = 80;
    private static final int PLAY_X = 50;
    private static final int PLAY_Y = 200;
    private static final int PLAY_WIDTH = 140;
    private static final int PLAY_HEIGHT = 50;

    private final Navigator navigator;
    private int enteredAt;
    private int shownStep;

    public CoverView(Navigator navigator) {
        this.navigator = navigator;
    }

    @Override
    public void enter() {
        enteredAt = Clock.millis();
        shownStep = -1;
    }

    @Override
    public void render() {
        int current = step(Clock.millis() - enteredAt);
        if (current == shownStep) {
            return;
        }
        shownStep = current;
        if (current == LOGO) {
            TftTouchShield.fillScreen(TftTouchShield.WHITE);
            JavaLogoImage.draw((WIDTH - JavaLogoImage.WIDTH) / 2, (HEIGHT - JavaLogoImage.HEIGHT) / 2);
        } else if (current == BOARD) {
            TftTouchShield.fillScreen(TftTouchShield.WHITE);
            BoardImage.draw((WIDTH - BoardImage.WIDTH) / 2, (HEIGHT - BoardImage.HEIGHT) / 2);
        } else {
            drawTitle();
        }
    }

    @Override
    public void touch(int x, int y) {
        if (shownStep == TITLE && isPlay(x, y)) {
            Serial.println("Miner2: opening the chain");
            navigator.show(Miner2Views.CHAIN);
        }
    }

    /** Which step of the cover shows {@code elapsedMillis} after it was entered. */
    static int step(int elapsedMillis) {
        if (elapsedMillis < STEP_MILLIS) {
            return LOGO;
        }
        return elapsedMillis < 2 * STEP_MILLIS ? BOARD : TITLE;
    }

    /** Whether ({@code x}, {@code y}) is on the PLAY button. */
    static boolean isPlay(int x, int y) {
        return x >= PLAY_X && x < PLAY_X + PLAY_WIDTH && y >= PLAY_Y && y < PLAY_Y + PLAY_HEIGHT;
    }

    private static void drawTitle() {
        TftTouchShield.fillScreen(GameScreen.BACKGROUND);
        TftTouchShield.setTextSize(TITLE_SIZE);
        TftTouchShield.setTextColor(TftTouchShield.GREEN, GameScreen.BACKGROUND);
        TftTouchShield.setCursor((WIDTH - 5 * 6 * TITLE_SIZE) / 2, TITLE_Y);
        TftTouchShield.print("MINER");
        TftTouchShield.fillRect(PLAY_X, PLAY_Y, PLAY_WIDTH, PLAY_HEIGHT, GameScreen.PANEL);
        TftTouchShield.drawRect(PLAY_X, PLAY_Y, PLAY_WIDTH, PLAY_HEIGHT, TftTouchShield.GREEN);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, GameScreen.PANEL);
        TftTouchShield.setCursor(PLAY_X + (PLAY_WIDTH - 4 * 18) / 2, PLAY_Y + (PLAY_HEIGHT - 21) / 2);
        TftTouchShield.print("PLAY");
    }
}
