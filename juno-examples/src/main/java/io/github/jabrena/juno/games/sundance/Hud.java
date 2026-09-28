package io.github.jabrena.juno.games.sundance;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Header, status values, centered text, and view clearing. */
final class Hud {
    static final int WIDTH = 320;
    static final int HEIGHT = 240;
    static final int HEADER = 20;
    static final int SPACE = TftTouchShield.BLACK;
    private static final int FRAME_COLOR = 0x4208;

    private static int status;
    private static int shownScore;
    private static boolean shownAutopilot;

    private Hud() {
    }

    static void drawHeader(int[] suns) {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER - 1, SPACE);
        TftTouchShield.drawHorizontalLine(0, HEADER - 1, WIDTH, FRAME_COLOR);
        invalidate();
        drawStatus(suns);
    }

    static void invalidate() {
        status = -1;
    }

    /** Score, round, lives, suns still to trap, time, and active pilot. */
    static void drawStatus(int[] suns) {
        int seconds = (Session.timeLeft + 24) / 25;
        int left = Session.remaining(suns);
        int combined = (Session.lives * 16 + left) * 128 + seconds;
        if (combined == status && Session.score == shownScore && Controls.autopilot == shownAutopilot) {
            return;
        }
        status = combined;
        shownScore = Session.score;
        shownAutopilot = Controls.autopilot;
        drawTopRow();
        drawBottomRow(left, seconds);
    }

    private static void drawTopRow() {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, SPACE);
        TftTouchShield.setCursor(4, 2);
        TftTouchShield.print("SCORE ");
        TftTouchShield.print(Session.score);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, SPACE);
        TftTouchShield.setCursor(136, 2);
        TftTouchShield.print(Controls.autopilot ? "CPU" : "HUMAN");
        TftTouchShield.print("     ");
        TftTouchShield.setCursor(250, 2);
        TftTouchShield.print("ROUND ");
        TftTouchShield.print(Session.round);
    }

    private static void drawBottomRow(int left, int seconds) {
        TftTouchShield.setTextColor(Session.lives == 1 ? TftTouchShield.RED : TftTouchShield.GREEN, SPACE);
        TftTouchShield.setCursor(4, 11);
        TftTouchShield.print("LIVES ");
        TftTouchShield.print(Session.lives);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(136, 11);
        TftTouchShield.print("SUNS ");
        TftTouchShield.print(left);
        TftTouchShield.print("  ");
        TftTouchShield.setTextColor(seconds <= 10 ? TftTouchShield.RED : TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(250, 11);
        TftTouchShield.print("TIME ");
        TftTouchShield.print(seconds);
        TftTouchShield.print(" ");
    }

    static void clearView() {
        TftTouchShield.fillRect(0, HEADER, WIDTH, HEIGHT - HEADER, SPACE);
        DisplayList.shown = 0;
    }

    static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
