package io.github.jabrena.juno.games.lunarlander;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Draws, erases, collision-tests, and explodes the rotated bitmap lander. */
final class LanderRenderer {
    private static final int SPRITE = 17;
    static final int HALF = 8;
    private static int shownX;
    private static int shownY;

    private LanderRenderer() {
    }

    static void startDescent(short[] ground, byte[] pads) {
        TftTouchShield.fillScreen(Terrain.SKY);
        TftTouchShield.fillRect(0, 0, Terrain.WIDTH, Hud.HEADER_HEIGHT, Hud.HEADER_BACKGROUND);
        Terrain.draw(ground, pads);
    }

    static void reset() {
        shownX = -1;
    }

    static boolean touchesGround(short[] ground) {
        float angle = (float) Math.toRadians(Flight.tilt * 15);
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        int centerX = Math.round(Flight.x);
        int centerY = Math.round(Flight.y);
        for (int dy = HALF; dy >= -HALF; dy--) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                int column = centerX + dx;
                if (column >= 0 && column < Terrain.WIDTH && centerY + dy >= ground[column]
                        && LanderSprite.color(dx, dy, sin, cos, false) >= 0) return true;
            }
        }
        return false;
    }

    static void draw(short[] ground, byte[] pads) {
        int left = Math.round(Flight.x) - HALF;
        int top = Math.round(Flight.y) - HALF;
        if (shownX >= 0 && (shownX != left || shownY != top)) erase(ground, pads, left, top);
        float angle = (float) Math.toRadians(Flight.tilt * 15);
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        if (!TftTouchShield.beginPixels(left, top, SPRITE, SPRITE)) return;
        for (int dy = -HALF; dy <= HALF; dy++) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                int color = LanderSprite.color(dx, dy, sin, cos, Flight.burning);
                TftTouchShield.pushPixel(color < 0 ? background(ground, pads, left + dx + HALF, top + dy + HALF)
                        : color);
            }
        }
        shownX = left;
        shownY = top;
    }

    private static void erase(short[] ground, byte[] pads, int left, int top) {
        for (int column = shownX; column < shownX + SPRITE; column++) {
            int from = shownY;
            int to = shownY + SPRITE;
            if (column >= left && column < left + SPRITE) {
                if (top > shownY) to = Math.min(to, top);
                else from = Math.max(from, top + SPRITE);
            }
            repaintRange(ground, pads, column, from, to);
        }
    }

    private static void repaintRange(short[] ground, byte[] pads, int column, int from, int to) {
        if (from >= to || column < 0 || column >= Terrain.WIDTH) return;
        int sky = Math.min(to, ground[column]);
        if (sky > from) TftTouchShield.drawVerticalLine(column, from, sky - from, Terrain.SKY);
        for (int row = Math.max(from, ground[column]); row < to; row++) {
            TftTouchShield.drawPixel(column, row, background(ground, pads, column, row));
        }
    }

    private static int background(short[] ground, byte[] pads, int column, int row) {
        if (column < 0 || column >= Terrain.WIDTH || row < ground[column]) return Terrain.SKY;
        if (pads[column] != 0 && row < ground[column] + 2) return Terrain.PAD;
        return Terrain.GROUND;
    }

    static void explode(short[] ground, byte[] pads) {
        int centerX = Math.round(Flight.x);
        int centerY = Math.min(Math.round(Flight.y), Terrain.PLAY_BOTTOM - 16);
        for (int radius = 3; radius <= 15; radius = radius + 3) {
            TftTouchShield.fillCircle(centerX, centerY, radius, TftTouchShield.YELLOW);
            TftTouchShield.fillCircle(centerX, centerY, radius - 2, TftTouchShield.RED);
            Delay.millis(60);
        }
        int left = Math.max(0, centerX - 15);
        int right = Math.min(Terrain.WIDTH - 1, centerX + 15);
        if (TftTouchShield.beginPixels(left, centerY - 15, right - left + 1, 31)) {
            for (int row = centerY - 15; row <= centerY + 15; row++) {
                for (int column = left; column <= right; column++) {
                    TftTouchShield.pushPixel(background(ground, pads, column, row));
                }
            }
        }
    }
}
