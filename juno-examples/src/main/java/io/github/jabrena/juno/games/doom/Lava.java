package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The lava of the cover: a still mosaic of coarse cells whose heat runs in diagonal bands, painted once behind the
 * cover's title. It no longer flows, which leaves the UNO Q's RAM to the map and its route.
 */
final class Lava {
    static final int CELL = 8;
    /** The lava's heat ramp as RGB565 constants (dark red up to yellow and back), so it is a flash table. */
    private static final int[] FLOW = {0x6840, 0xB8C0, 0xF2C1, 0xFD02, 0xFEA8, 0xFD02, 0xF2C1, 0xB8C0};

    private Lava() {
    }

    /** The whole screen covered with lava, in coarse cells so it stays cheap. */
    static void paint() {
        for (int y = 0; y < DisplayList.HEIGHT; y += CELL) {
            for (int x = 0; x < DisplayList.WIDTH; x += CELL) {
                TftTouchShield.fillRect(x, y, CELL, CELL, tone(x, y));
            }
        }
    }

    /** The lava's color at a pixel, so cut-out corners match the cells around them. */
    static int tone(int x, int y) {
        return FLOW[((x / CELL + y / CELL) / 2 + (x + y) / 64) % FLOW.length];
    }
}
