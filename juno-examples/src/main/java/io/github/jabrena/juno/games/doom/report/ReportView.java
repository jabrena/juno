package io.github.jabrena.juno.games.doom.report;

import io.github.jabrena.juno.games.doom.commons.DoomState;
import io.github.jabrena.juno.games.doom.commons.DoomViews;
import io.github.jabrena.juno.games.doom.commons.Screen;
import io.github.jabrena.juno.games.doom.engine.DoomEngine;
import io.github.jabrena.juno.games.doom.map.DoomMapLoader;
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

    private final TftTouchShield tft;
    private static final int BLINK_FRAMES = 21;
    private static final int VISIBLE_FRAMES = 13;
    private static final int BAR_X = 70;
    private static final int BAR_Y = 203;
    private static final int BAR_WIDTH = 160;
    private static final int MESSAGE_Y = 215;
    private static final int EPISODE_DONE = -2;

    private final Navigator navigator;
    private final DoomState model;
    private final DoomEngine engine;
    private final DoomMapLoader loader;
    private int shownStatus;
    private int frame;

    public ReportView(TftTouchShield tft, Navigator navigator, DoomState model, DoomEngine engine, DoomMapLoader loader) {
        this.tft = tft;
        this.navigator = navigator;
        this.model = model;
        this.engine = engine;
        this.loader = loader;
    }

    @Override
    public void enter() {
        tft.fillScreen(Screen.BACKGROUND);
        Screen.plaque(tft, 14, 8, 292, 224);
        showMapName(tft, 20, 3, TftTouchShield.YELLOW);
        Screen.showCentered(tft, "FINISHED", 49, 3, TftTouchShield.RED);
        statistic(tft, "KILLS", engine.kills(), engine.monsters(), 91);
        statistic(tft, "ITEMS", engine.itemsFound(), engine.itemsCounted(), 121);
        statistic(tft, "SECRETS", engine.secretsFound(), engine.secrets(), 151);
        time(tft, engine.elapsedMillis(), 183);
        shownStatus = -1;
        frame = 0;
        if (model.lastMap()) {
            shownStatus = EPISODE_DONE;
        } else {
            model.advanceMap();
            loader.begin(model.episode(), model.map(), model.skill(), BAR_X, BAR_Y, BAR_WIDTH);
        }
        Serial.println("Doom: report view active");
    }

    @Override
    public void render() {
        if (shownStatus == EPISODE_DONE) {
            blink(tft, "EPISODE COMPLETE - TAP TO CONTINUE");
            return;
        }
        int status = loader.status();
        if (status != shownStatus) {
            shownStatus = status;
            clearMessage(tft);
            if (status == DoomMapLoader.LOADING) {
                showNextMap(tft, "LOADING ", "");
            } else if (status == DoomMapLoader.FAILED) {
                showNextMap(tft, "", " FAILED - TAP FOR TITLE");
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
                showNextMap(tft, "TAP TO PLAY ", "");
            } else if (phase == VISIBLE_FRAMES) {
                clearMessage(tft);
            }
            frame = frame + 1;
        }
    }

    @Override
    public void touch(int x, int y) {
        int status = shownStatus == EPISODE_DONE ? EPISODE_DONE : loader.status();
        if (status == DoomMapLoader.READY) {
            Serial.println("Doom: starting next map");
            navigator.show(DoomViews.GAMEPLAY);
        } else if (status == EPISODE_DONE || status == DoomMapLoader.FAILED) {
            Serial.println("Doom: returning to cover");
            navigator.show(DoomViews.COVER);
        }
    }

    private void blink(TftTouchShield tft, String text) {
        int phase = frame % BLINK_FRAMES;
        if (phase == 0) {
            Screen.showCentered(tft, text, MESSAGE_Y, 1, TftTouchShield.YELLOW);
        } else if (phase == VISIBLE_FRAMES) {
            clearMessage(tft);
        }
        frame = frame + 1;
    }

    private void clearMessage(TftTouchShield tft) {
        tft.fillRect(18, MESSAGE_Y, Screen.WIDTH - 36, 8, Screen.BACKGROUND);
    }

    /** The finished map's EeMm name, centered; the model still holds it until the next map is requested. */
    private void showMapName(TftTouchShield tft, int y, int size, int color) {
        tft.setTextSize(size);
        tft.setTextColor(color, Screen.BACKGROUND);
        tft.setCursor((Screen.WIDTH - 4 * 6 * size) / 2, y);
        printMap(tft);
    }

    private void showNextMap(TftTouchShield tft, String prefix, String suffix) {
        int characters = prefix.length() + 4 + suffix.length();
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.YELLOW, Screen.BACKGROUND);
        tft.setCursor((Screen.WIDTH - characters * 6) / 2, MESSAGE_Y);
        tft.print(prefix);
        printMap(tft);
        tft.print(suffix);
    }

    private void printMap(TftTouchShield tft) {
        tft.print("E");
        tft.print(model.episode());
        tft.print("M");
        tft.print(model.map());
    }

    private void statistic(TftTouchShield tft, String label, int found, int total, int y) {
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.RED, Screen.BACKGROUND);
        tft.setCursor(32, y);
        tft.print(label);
        tft.setCursor(140, y);
        tft.print(found);
        tft.print("/");
        tft.print(total);
        tft.setCursor(248, y);
        tft.print(DoomEngine.percent(found, total));
        tft.print("%");
    }

    private void time(TftTouchShield tft, int elapsedMillis, int y) {
        int seconds = Math.max(0, elapsedMillis / 1000);
        int minutes = Math.min(99, seconds / 60);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.RED, Screen.BACKGROUND);
        tft.setCursor(32, y);
        tft.print("TIME");
        tft.setCursor(196, y);
        twoDigits(tft, minutes);
        tft.print(":");
        twoDigits(tft, seconds % 60);
    }

    private static void twoDigits(TftTouchShield tft, int value) {
        if (value < 10) {
            tft.print("0");
        }
        tft.print(value);
    }

}
