package io.github.jabrena.juno.games.doom.map;

import io.github.jabrena.juno.games.doom.commons.DoomState;
import io.github.jabrena.juno.games.doom.commons.DoomViews;
import io.github.jabrena.juno.games.doom.commons.Screen;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/**
 * Loads the map chosen on the episode screen once startup has released the SD card. The screen is drawn first; the
 * next frame then holds while the engine reads the map and plans its route, drawing its own progress bar with the
 * seconds left, and once the map is ready a tap starts it.
 */
public final class DoomMapLoadingView implements View {

    private final TftTouchShield tft;
    private static final int BAR_X = 40;
    private static final int BAR_Y = 134;
    private static final int BAR_WIDTH = 200;
    private static final int WAITING = -2;
    private static final int BLINK_FRAMES = 21;
    private static final int VISIBLE_FRAMES = 13;
    private static final int PROMPT_Y = 180;

    private final Navigator navigator;
    private final DoomState model;
    private final DoomMapLoader loader;
    private int shownStatus = -1;
    private boolean requested;
    private int frame;

    public DoomMapLoadingView(TftTouchShield tft, Navigator navigator, DoomState model, DoomMapLoader loader) {
        this.tft = tft;
        this.navigator = navigator;
        this.model = model;
        this.loader = loader;
    }

    @Override
    public void enter() {
        tft.fillScreen(Screen.BACKGROUND);
        Screen.showCentered(tft, "ENTERING", 24, 4, TftTouchShield.RED);
        drawMapName(tft);
        Screen.drawBack(tft);
        shownStatus = -1;
        requested = false;
        frame = 0;
        requestWhenCardIsFree();
        refresh(tft);
        Serial.println("Doom: map loading view active");
    }

    @Override
    public void render() {
        requestWhenCardIsFree();
        refresh(tft);
        loader.step();
        refresh(tft);
        blinkPrompt(tft);
    }

    @Override
    public void touch(int x, int y) {
        if (Screen.isBack(x, y)) {
            loader.cancel();
            Serial.println("Doom: map loading cancelled");
            navigator.back();
        } else if (requested && loader.status() == DoomMapLoader.READY) {
            Serial.println("Doom: starting map");
            navigator.show(DoomViews.GAMEPLAY);
        }
    }

    private void blinkPrompt(TftTouchShield tft) {
        if (!requested || loader.status() != DoomMapLoader.READY) {
            return;
        }
        boolean on = frame % BLINK_FRAMES < VISIBLE_FRAMES;
        if (frame % BLINK_FRAMES == 0 || frame % BLINK_FRAMES == VISIBLE_FRAMES) {
            Screen.showCentered(tft, on ? "TAP TO PLAY" : "           ", PROMPT_Y, 2, TftTouchShield.WHITE);
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

    private void refresh(TftTouchShield tft) {
        int status = requested ? loader.status() : WAITING;
        if (status != shownStatus) {
            shownStatus = status;
            tft.fillRect(0, 92, Screen.WIDTH, 22, Screen.BACKGROUND);
            if (status == WAITING) {
                Screen.showCentered(tft, "WAITING FOR SD CARD...", 96, 2, TftTouchShield.YELLOW);
            } else if (status == DoomMapLoader.LOADING) {
                Screen.showCentered(tft, "LOADING MAP...", 96, 2, TftTouchShield.YELLOW);
            } else if (status == DoomMapLoader.READY) {
                Screen.showCentered(tft, "MAP READY", 96, 2, TftTouchShield.GREEN);
            } else if (status == DoomMapLoader.FAILED) {
                Screen.showCentered(tft, "MAP LOAD FAILED", 96, 2, TftTouchShield.RED);
            }
        }
    }

    private void drawMapName(TftTouchShield tft) {
        tft.setTextSize(3);
        tft.setTextColor(TftTouchShield.WHITE, Screen.BACKGROUND);
        tft.setCursor(124, 66);
        tft.print("E");
        tft.print(model.episode());
        tft.print("M");
        tft.print(model.map());
    }

}
