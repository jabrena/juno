package io.github.jabrena.juno.games.lunarlander;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Defines the pixels of the lander and rotates them around its center. */
final class LanderSprite {
    private static final int HULL = 0xC618;
    private static final int WINDOW = TftTouchShield.CYAN;
    private static final int FLAME = TftTouchShield.ORANGE;

    private LanderSprite() {
    }

    private static int hullRow(int row) {
        if (row == 0) return 0x01C0;
        if (row == 1 || row == 6) return 0x03E0;
        if (row >= 2 && row <= 5) return 0x07F0;
        if (row == 7) return 0x0FF8;
        if (row == 8) return 0x1FFC;
        if (row == 9) return 0x1BEC;
        if (row == 10) return 0x0808 | 0x01C0;
        if (row == 11 || row == 12) return 0x1004;
        if (row == 13) return 0x2002;
        return 0x7007;
    }

    private static int windowRow(int row) {
        if (row == 2 || row == 4) return 0x0180;
        return row == 3 ? 0x03C0 : 0;
    }

    private static int flameRow(int row) {
        if (row == 11 || row == 12) return 0x01C0;
        return row == 13 || row == 14 ? 0x0080 : 0;
    }

    static int color(int dx, int dy, float sin, float cos, boolean flame) {
        int sx = Math.round(cos * dx + sin * dy) + 7;
        int sy = Math.round(-sin * dx + cos * dy) + 7;
        if (sx < 0 || sx > 14 || sy < 0 || sy > 14) return -1;
        int bit = 1 << (14 - sx);
        if ((windowRow(sy) & bit) != 0) return WINDOW;
        if ((hullRow(sy) & bit) != 0) return HULL;
        return flame && (flameRow(sy) & bit) != 0 ? FLAME : -1;
    }
}
