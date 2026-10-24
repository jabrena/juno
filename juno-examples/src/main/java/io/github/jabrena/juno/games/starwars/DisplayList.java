package io.github.jabrena.juno.games.starwars;

import io.github.jabrena.juno.games.common.TftLineDisplayList;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** The Star Wars view's shared double-buffered line display list. */
final class DisplayList {
    static final int WIDTH = 320;
    static final int HEIGHT = 240;
    static final int HEADER = 20;
    static final int SPACE = TftTouchShield.BLACK;
    static final int MAX_LINES = 180;
    static final int LIST_SIZE = MAX_LINES * 5;
    static int front;
    static int shown;
    static int built;

    private static final TftLineDisplayList LINES =
            new TftLineDisplayList(WIDTH, HEIGHT, HEADER, MAX_LINES, SPACE);

    private DisplayList() {
    }

    static void begin() {
        loadState();
        LINES.begin();
        saveState();
    }

    static void forget() {
        loadState();
        LINES.forget();
        saveState();
    }

    static void addLine(short[] lines, int x0, int y0, int x1, int y1, int color) {
        loadState();
        LINES.addLine(lines, x0, y0, x1, y1, color);
        saveState();
    }

    static void present(short[] lines) {
        loadState();
        LINES.present(lines);
        saveState();
    }

    static void clearView() {
        loadState();
        LINES.clearView();
        saveState();
    }

    private static void loadState() {
        LINES.setState(front, shown, built);
    }

    private static void saveState() {
        front = LINES.front();
        shown = LINES.shown();
        built = LINES.built();
    }
}
