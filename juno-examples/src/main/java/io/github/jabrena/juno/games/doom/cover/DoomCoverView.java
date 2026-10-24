package io.github.jabrena.juno.games.doom.cover;

import io.github.jabrena.juno.games.doom.commons.DoomState;
import io.github.jabrena.juno.games.doom.commons.DoomViews;
import io.github.jabrena.juno.games.doom.commons.Screen;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** DOOM's lava cover and blinking prompt, isolated as the first Doom view. */
public final class DoomCoverView implements View {
    private static final int HEIGHT = 240;
    private static final int CELL = 8;
    private static final int TITLE_SIZE = 8;
    private static final int TITLE_Y = 56;
    private static final int PLAQUE_MARGIN = 12;
    private static final int PROMPT_X = 80;
    private static final int PROMPT_Y = 194;
    private static final int PROMPT_WIDTH = 160;
    private static final int PROMPT_HEIGHT = 28;
    private static final int[] LAVA = {0x6840, 0xB8C0, 0xF2C1, 0xFD02, 0xFEA8, 0xFD02, 0xF2C1, 0xB8C0};

    private final Navigator navigator;
    private final DoomState model;
    private boolean shownPrompt;

    public DoomCoverView(Navigator navigator, DoomState model) {
        this.navigator = navigator;
        this.model = model;
    }

    @Override
    public void enter() {
        paintLava();
        int titleWidth = 4 * 6 * TITLE_SIZE;
        Screen.plaque((Screen.WIDTH - titleWidth) / 2 - PLAQUE_MARGIN, TITLE_Y - PLAQUE_MARGIN,
                titleWidth + 2 * PLAQUE_MARGIN, 7 * TITLE_SIZE + 2 * PLAQUE_MARGIN);
        Screen.showCentered("DOOM", TITLE_Y, TITLE_SIZE, TftTouchShield.RED);
        Screen.plaque(PROMPT_X, PROMPT_Y, PROMPT_WIDTH, PROMPT_HEIGHT);
        model.resetCover();
        shownPrompt = model.coverPromptVisible();
        drawPrompt(shownPrompt);
        Serial.println("Doom: cover view active");
    }

    @Override
    public void render() {
        model.advanceCover();
        boolean promptVisible = model.coverPromptVisible();
        if (promptVisible != shownPrompt) {
            shownPrompt = promptVisible;
            drawPrompt(promptVisible);
        }
    }

    @Override
    public void touch(int x, int y) {
        if (isPlay(x, y)) {
            Serial.println("Doom: opening pilot view");
            navigator.show(DoomViews.PILOT);
        }
    }

    static boolean isPlay(int x, int y) {
        return x >= PROMPT_X && x < PROMPT_X + PROMPT_WIDTH && y >= PROMPT_Y && y < PROMPT_Y + PROMPT_HEIGHT;
    }

    private void paintLava() {
        for (int y = 0; y < HEIGHT; y += CELL) {
            for (int x = 0; x < Screen.WIDTH; x += CELL) {
                TftTouchShield.fillRect(x, y, CELL, CELL, lavaTone(x, y));
            }
        }
    }

    private int lavaTone(int x, int y) {
        return LAVA[((x / CELL + y / CELL) / 2 + (x + y) / 64) % LAVA.length];
    }

    private void drawPrompt(boolean visible) {
        Screen.showCentered(visible ? "TAP TO PLAY" : "           ", PROMPT_Y + 8, 2, TftTouchShield.WHITE);
    }

}
