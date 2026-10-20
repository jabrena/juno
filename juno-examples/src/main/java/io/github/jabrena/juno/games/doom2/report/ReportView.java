package io.github.jabrena.juno.games.doom2.report;

import io.github.jabrena.juno.games.doom.DoomEngine;
import io.github.jabrena.juno.games.doom2.commons.Doom2State;
import io.github.jabrena.juno.games.doom2.commons.Doom2Views;
import io.github.jabrena.juno.games.doom2.commons.Screen;
import io.github.jabrena.juno.games.doom2.map.DoomMapLoader;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/**
 * DOOM's intermission after an exit: the finished map's kills, items, secrets and time. The episode's next map is
 * read under the tally, with the engine's progress bar, and a tap then plays it; after the episode's last map, or if
 * the next one cannot be read, the tap returns to the cover.
 */
public final class ReportView implements View {
    private static final int BLINK_FRAMES = 21;
    private static final int VISIBLE_FRAMES = 13;
    private static final int BAR_X = 70;
    private static final int BAR_Y = 203;
    private static final int BAR_WIDTH = 160;
    private static final int MESSAGE_Y = 215;
    private static final int EPISODE_DONE = -2;

    private final Navigator navigator;
    private final Doom2State model;
    private final DoomEngine engine;
    private final DoomMapLoader loader;
    private int shownStatus;
    private int frame;

    public ReportView(Navigator navigator, Doom2State model, DoomEngine engine, DoomMapLoader loader) {
        this.navigator = navigator;
        this.model = model;
        this.engine = engine;
        this.loader = loader;
    }

    @Override
    public void enter() {
        TftTouchShield.fillScreen(Screen.BACKGROUND);
        Screen.plaque(14, 8, 292, 224);
        showMapName(20, 3, TftTouchShield.YELLOW);
        Screen.showCentered("FINISHED", 49, 3, TftTouchShield.RED);
        statistic("KILLS", engine.kills(), engine.monsters(), 91);
        statistic("ITEMS", engine.itemsFound(), engine.itemsCounted(), 121);
        statistic("SECRETS", engine.secretsFound(), engine.secrets(), 151);
        time(engine.elapsedMillis(), 183);
        shownStatus = -1;
        frame = 0;
        if (model.lastMap()) {
            shownStatus = EPISODE_DONE;
        } else {
            model.advanceMap();
            loader.begin(model.episode(), model.map(), model.skill(), BAR_X, BAR_Y, BAR_WIDTH);
        }
        Serial.println("Doom2: report view active");
    }

    @Override
    public void render() {
        if (shownStatus == EPISODE_DONE) {
            blink("EPISODE COMPLETE - TAP TO CONTINUE");
            return;
        }
        int status = loader.status();
        if (status != shownStatus) {
            shownStatus = status;
            clearMessage();
            if (status == DoomMapLoader.LOADING) {
                showNextMap("LOADING ", "");
            } else if (status == DoomMapLoader.FAILED) {
                showNextMap("", " FAILED - TAP FOR TITLE");
            }
        }
        if (status == DoomMapLoader.LOADING) {
            // The message is up; the frame now holds while the next map loads under the tally.
            loader.step();
            return;
        }
        if (status == DoomMapLoader.READY) {
            int phase = frame % BLINK_FRAMES;
            if (phase == 0) {
                showNextMap("TAP TO PLAY ", "");
            } else if (phase == VISIBLE_FRAMES) {
                clearMessage();
            }
            frame = frame + 1;
        }
    }

    @Override
    public void touch(int x, int y) {
        int status = shownStatus == EPISODE_DONE ? EPISODE_DONE : loader.status();
        if (status == DoomMapLoader.READY) {
            Serial.println("Doom2: starting next map");
            navigator.show(Doom2Views.GAMEPLAY);
        } else if (status == EPISODE_DONE || status == DoomMapLoader.FAILED) {
            Serial.println("Doom2: returning to cover");
            navigator.show(Doom2Views.COVER);
        }
    }

    private void blink(String text) {
        int phase = frame % BLINK_FRAMES;
        if (phase == 0) {
            Screen.showCentered(text, MESSAGE_Y, 1, TftTouchShield.YELLOW);
        } else if (phase == VISIBLE_FRAMES) {
            clearMessage();
        }
        frame = frame + 1;
    }

    private void clearMessage() {
        TftTouchShield.fillRect(18, MESSAGE_Y, Screen.WIDTH - 36, 8, Screen.BACKGROUND);
    }

    /** The finished map's EeMm name, centered; the model still holds it until the next map is requested. */
    private void showMapName(int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, Screen.BACKGROUND);
        TftTouchShield.setCursor((Screen.WIDTH - 4 * 6 * size) / 2, y);
        printMap();
    }

    private void showNextMap(String prefix, String suffix) {
        int characters = prefix.length() + 4 + suffix.length();
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, Screen.BACKGROUND);
        TftTouchShield.setCursor((Screen.WIDTH - characters * 6) / 2, MESSAGE_Y);
        TftTouchShield.print(prefix);
        printMap();
        TftTouchShield.print(suffix);
    }

    private void printMap() {
        TftTouchShield.print("E");
        TftTouchShield.print(model.episode());
        TftTouchShield.print("M");
        TftTouchShield.print(model.map());
    }

    private void statistic(String label, int found, int total, int y) {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.RED, Screen.BACKGROUND);
        TftTouchShield.setCursor(32, y);
        TftTouchShield.print(label);
        TftTouchShield.setCursor(140, y);
        TftTouchShield.print(found);
        TftTouchShield.print("/");
        TftTouchShield.print(total);
        TftTouchShield.setCursor(248, y);
        TftTouchShield.print(DoomEngine.percent(found, total));
        TftTouchShield.print("%");
    }

    private void time(int elapsedMillis, int y) {
        int seconds = Math.max(0, elapsedMillis / 1000);
        int minutes = Math.min(99, seconds / 60);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.RED, Screen.BACKGROUND);
        TftTouchShield.setCursor(32, y);
        TftTouchShield.print("TIME");
        TftTouchShield.setCursor(196, y);
        twoDigits(minutes);
        TftTouchShield.print(":");
        twoDigits(seconds % 60);
    }

    private static void twoDigits(int value) {
        if (value < 10) {
            TftTouchShield.print("0");
        }
        TftTouchShield.print(value);
    }

}
