package io.github.jabrena.juno.usecases.gladiator.screen;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.gladiator.view.ClassWireframe;
import io.github.jabrena.juno.usecases.gladiator.view.Figure;
import io.github.jabrena.juno.usecases.gladiator.view.Hud;
import io.github.jabrena.juno.usecases.gladiator.view.ViewList;
import io.github.jabrena.juno.util.Delay;

/** The CPU-opponent reveal and the final result screens around a Solo bout. */
public final class SoloPresentation {
    private static final Clock CLOCK = ArduinoUno.getClock();
    public static final int RESULT_MILLIS = 5000;

    private static final int FRAME_MILLIS = 50;
    private static final int PLAY_X = 20;
    private static final int PLAY_Y = 206;
    private static final int PLAY_WIDTH = 280;
    private static final int PLAY_HEIGHT = 28;
    private static final int GOLD = 0xE6C0;

    private SoloPresentation() {
    }

    /** Rotates the selected player and random CPU opponent together, drawn into {@code list}, until PLAY is tapped. */
    public static void showOpponent(TftTouchShield tft, ViewList list, ClassWireframe wireframe, FighterProfile player, FighterProfile opponent) {
        drawVersus(tft, list, player, opponent);
        int started = CLOCK.millis();
        boolean touching = true;
        while (true) {
            wireframe.buildVersus(player.kind(), opponent.kind(), (CLOCK.millis() - started) * 0.0025f);
            list.present(tft);
            if (tft.readTouch()) {
                if (!touching && onPlay(tft.touchX(), tft.touchY())) {
                    waitForRelease(tft);
                    return;
                }
                touching = true;
            } else {
                touching = false;
            }
            Delay.millis(FRAME_MILLIS);
        }
    }

    public static boolean onPlay(int x, int y) {
        return x >= PLAY_X && x < PLAY_X + PLAY_WIDTH && y >= PLAY_Y && y < PLAY_Y + PLAY_HEIGHT;
    }

    /** Shows who won for five seconds before {@link Gladiator} returns to the title. */
    public static void showResult(TftTouchShield tft, boolean playerWon, FighterProfile player, FighterProfile opponent) {
        drawResult(tft, playerWon, player, opponent);
        Delay.millis(RESULT_MILLIS);
    }

    public static void drawResult(TftTouchShield tft, boolean playerWon, FighterProfile player, FighterProfile opponent) {
        FighterProfile winner = playerWon ? player : opponent;
        int color = playerWon ? TftTouchShield.GREEN : TftTouchShield.RED;
        tft.fillScreen(ViewList.BACKGROUND);
        tft.drawHorizontalLine(24, 38, ViewList.WIDTH - 48, GOLD);
        tft.drawHorizontalLine(24, 184, ViewList.WIDTH - 48, GOLD);
        Hud.centered(tft, playerWon ? "VICTORY" : "DEFEAT", 56, 3, color, ViewList.BACKGROUND);
        Hud.centered(tft, winner.displayName() + " WINS", 112, 2, winner.color(), ViewList.BACKGROUND);
        Hud.centered(tft, Opening.className(winner.kind()) + " / " + Figure.name(winner.behaviour()), 145, 1,
                TftTouchShield.WHITE, ViewList.BACKGROUND);
        Hud.centered(tft, "RETURNING TO TITLE", 204, 1, TftTouchShield.GRAY, ViewList.BACKGROUND);
    }

    public static void drawVersus(TftTouchShield tft, ViewList list, FighterProfile player, FighterProfile opponent) {
        tft.fillScreen(ViewList.BACKGROUND);
        list.clearView(tft);
        centeredHalf(tft, 0, "PLAYER CPU", 8, 1, TftTouchShield.GRAY);
        centeredHalf(tft, 0, player.displayName(), 23, 2, player.color());
        centeredHalf(tft, 0, Opening.className(player.kind()), 49, 1, TftTouchShield.WHITE);
        centeredHalf(tft, 0, Figure.name(player.behaviour()), 63, 1, Figure.color(player.behaviour()));
        centeredHalf(tft, 160, "OPPONENT CPU", 8, 1, TftTouchShield.GRAY);
        centeredHalf(tft, 160, opponent.displayName(), 23, 2, opponent.color());
        centeredHalf(tft, 160, Opening.className(opponent.kind()), 49, 1, TftTouchShield.WHITE);
        centeredHalf(tft, 160, Figure.name(opponent.behaviour()), 63, 1, Figure.color(opponent.behaviour()));
        Hud.centered(tft, "VS", 80, 2, GOLD, ViewList.BACKGROUND);
        tft.fillRect(PLAY_X, PLAY_Y, PLAY_WIDTH, PLAY_HEIGHT, Hud.PANEL);
        tft.drawRect(PLAY_X, PLAY_Y, PLAY_WIDTH, PLAY_HEIGHT, GOLD);
        Hud.centered(tft, "PLAY", PLAY_Y + 6, 2, GOLD, Hud.PANEL);
    }

    private static void centeredHalf(TftTouchShield tft, int left, String value, int y, int size, int color) {
        tft.setTextSize(size);
        tft.setTextColor(color, ViewList.BACKGROUND);
        tft.setCursor(left + (160 - value.length() * TftTouchShield.CHAR_WIDTH * size) / 2, y);
        tft.print(value);
    }

    private static void waitForRelease(TftTouchShield tft) {
        int misses = 0;
        while (misses < 3) {
            if (tft.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }
}
