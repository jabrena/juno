package io.github.jabrena.juno.games.doom2.map;

import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.mvc.Navigator;
import io.github.jabrena.juno.api.mvc.View;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.games.doom2.commons.Doom2State;
import io.github.jabrena.juno.games.doom2.commons.Doom2Views;
import io.github.jabrena.juno.games.doom2.commons.Screen;

/**
 * Loads the map chosen on the episode screen once startup has released the SD card. The screen is drawn first; the
 * next frame then holds while the engine reads the map and plans its route, drawing its own progress bar with the
 * seconds left, and once the map is ready a tap starts it.
 */
public final class DoomMapLoadingView implements View {
    private static final int BAR_X = 40;
    private static final int BAR_Y = 134;
    private static final int BAR_WIDTH = 200;
    private static final int WAITING = -2;
    private static final int BLINK_FRAMES = 21;
    private static final int VISIBLE_FRAMES = 13;
    private static final int PROMPT_Y = 180;

    private final Navigator navigator;
    private final Doom2State model;
    private final DoomMapLoader loader;
    private int shownStatus = -1;
    private boolean requested;
    private int frame;

    public DoomMapLoadingView(
            Navigator navigator, Doom2State model, DoomMapLoader loader) {
        this.navigator = navigator;
        this.model = model;
        this.loader = loader;
    }

    @Override
    public void enter() {
        TftTouchShield.fillScreen(Screen.BACKGROUND);
        Screen.showCentered("ENTERING", 24, 4, TftTouchShield.RED);
        drawMapName();
        Screen.drawBack();
        shownStatus = -1;
        requested = false;
        frame = 0;
        requestWhenCardIsFree();
        refresh();
        Serial.println("Doom2: map loading view active");
    }

    @Override
    public void render() {
        requestWhenCardIsFree();
        refresh();
        loader.step();
        refresh();
        blinkPrompt();
    }

    @Override
    public void touch(int x, int y) {
        if (Screen.isBack(x, y)) {
            loader.cancel();
            Serial.println("Doom2: map loading cancelled");
            navigator.back();
        } else if (requested && loader.status() == DoomMapLoader.READY) {
            Serial.println("Doom2: starting map");
            navigator.show(Doom2Views.GAMEPLAY);
        }
    }

    private void blinkPrompt() {
        if (!requested || loader.status() != DoomMapLoader.READY) {
            return;
        }
        boolean on = frame % BLINK_FRAMES < VISIBLE_FRAMES;
        if (frame % BLINK_FRAMES == 0 || frame % BLINK_FRAMES == VISIBLE_FRAMES) {
            Screen.showCentered(on ? "TAP TO PLAY" : "           ", PROMPT_Y, 2, TftTouchShield.WHITE);
        }
        frame = frame + 1;
    }

    /** Startup still owns the SD card until it publishes completion; the map is requested only after that. */
    private void requestWhenCardIsFree() {
        if (!requested && model.startupComplete()) {
            requested = true;
            loader.begin(model.episode(), model.map(), model.skill(), BAR_X, BAR_Y, BAR_WIDTH);
        }
    }

    private void refresh() {
        int status = requested ? loader.status() : WAITING;
        if (status != shownStatus) {
            shownStatus = status;
            TftTouchShield.fillRect(0, 92, Screen.WIDTH, 22, Screen.BACKGROUND);
            if (status == WAITING) {
                Screen.showCentered("WAITING FOR SD CARD...", 96, 2, TftTouchShield.YELLOW);
            } else if (status == DoomMapLoader.LOADING) {
                Screen.showCentered("LOADING MAP...", 96, 2, TftTouchShield.YELLOW);
            } else if (status == DoomMapLoader.READY) {
                Screen.showCentered("MAP READY", 96, 2, TftTouchShield.GREEN);
            } else if (status == DoomMapLoader.FAILED) {
                Screen.showCentered("MAP LOAD FAILED", 96, 2, TftTouchShield.RED);
            }
        }
    }

    private void drawMapName() {
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, Screen.BACKGROUND);
        TftTouchShield.setCursor(124, 66);
        TftTouchShield.print("E");
        TftTouchShield.print(model.episode());
        TftTouchShield.print("M");
        TftTouchShield.print(model.map());
    }

}
