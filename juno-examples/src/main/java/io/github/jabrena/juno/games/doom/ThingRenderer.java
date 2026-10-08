package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Things drawn after the walls: items, monsters, corpses and fireballs as billboards that always face
 * the marine, each line kept only in the columns where it stands nearer than the wall {@link Renderer}
 * closed that column with; then the pistol, its muzzle flash, the crosshair and the red frame of a
 * fresh wound over the view.
 */
final class ThingRenderer {
    private static final int NEAREST_THING = 16;
    private static final float FOCAL = Renderer.FOCAL;
    private static final int CX = Renderer.CX;
    private static final int CY = Renderer.CY;
    private static final int GUN = TftTouchShield.color(170, 170, 180);
    private static final int CROSSHAIR = TftTouchShield.color(90, 90, 90);

    private static short[] depths;
    private static float viewX;
    private static float viewY;
    private static float viewZ;
    private static float cos;
    private static float sin;
    // A sprite line after clipping to the view.
    private static float clipX0;
    private static float clipY0;
    private static float clipX1;
    private static float clipY1;

    private ThingRenderer() {
    }

    static void draw(short[] lines, short[] columnDepths, short[] monsters, short[] shots, byte[] taken) {
        depths = columnDepths;
        viewX = Player.x;
        viewY = Player.y;
        viewZ = Player.eye;
        cos = (float) Math.cos(Player.angle);
        sin = (float) Math.sin(Player.angle);
        drawItems(lines, taken);
        drawThings(lines, monsters, shots);
        drawOverlay(lines);
    }

    private static void drawItems(short[] lines, byte[] taken) {
        for (int i = 0; i < Level.ITEMS; i++) {
            if (taken[i] == 0) {
                float x = Level.ITEM_X[i];
                float y = Level.ITEM_Y[i];
                int kind = Level.ITEM_KIND[i];
                drawShape(lines, Sprites.itemShape(kind), x, y, Level.SECTOR_FLOOR[Player.sectorAt(x, y)],
                        Sprites.itemColor(kind));
            }
        }
    }

    private static void drawThings(short[] lines, short[] monsters, short[] shots) {
        for (int i = 0; i < Level.MONSTERS; i++) {
            int at = i * Monsters.STRIDE;
            int state = monsters[at + Monsters.STATE];
            int kind = monsters[at + Monsters.KIND];
            float x = monsters[at + Monsters.X];
            float y = monsters[at + Monsters.Y];
            float floor = monsters[at + Monsters.FLOOR];
            if (state == Monsters.DEAD) {
                drawShape(lines, Sprites.CORPSE, x, y, floor, Sprites.CORPSE_COLOR);
                continue;
            }
            drawShape(lines, kind, x, y, floor, state == Monsters.PAIN ? Sprites.PAIN_COLOR : Sprites.color(kind));
            if (monsters[at + Monsters.FLASH] > 0) {
                drawShape(lines, Sprites.FLASH, x, y, floor, Sprites.FLASH_COLOR);
            }
        }
        for (int s = 0; s < Monsters.SHOTS; s++) {
            int shot = s * Monsters.SHOT_STRIDE;
            if (shots[shot + Monsters.LIFE] > 0) {
                drawShape(lines, Sprites.FIREBALL, shots[shot + Monsters.SHOT_X], shots[shot + Monsters.SHOT_Y],
                        shots[shot + Monsters.SHOT_Z], Sprites.FIREBALL_COLOR);
            }
        }
    }

    /** A billboard: the shape stands at (wx, wy) with its origin at height {@code baseZ}, facing the view. */
    private static void drawShape(short[] lines, int shape, float wx, float wy, float baseZ, int color) {
        float x = wx - viewX;
        float y = wy - viewY;
        float depth = x * cos + y * sin;
        if (depth < NEAREST_THING) {
            return;
        }
        float scale = FOCAL / depth;
        float centerX = CX + (x * sin - y * cos) * scale;
        float baseY = CY - (baseZ - viewZ) * scale;
        if (centerX < -40 * scale || centerX > DisplayList.WIDTH + 40 * scale) {
            return;
        }
        int distance = Math.round(depth);
        for (int i = Sprites.START[shape]; i < Sprites.START[shape + 1]; i += 4) {
            spriteLine(lines, centerX + Sprites.SEGMENTS[i] * scale, baseY - Sprites.SEGMENTS[i + 1] * scale,
                    centerX + Sprites.SEGMENTS[i + 2] * scale, baseY - Sprites.SEGMENTS[i + 3] * scale,
                    distance, color);
        }
    }

    /** Draws a sprite line in the columns where no nearer wall hides it. */
    private static void spriteLine(short[] lines, float x0, float y0, float x1, float y1, int distance, int color) {
        if (!clip(x0, y0, x1, y1)) {
            return;
        }
        if (clipX0 > clipX1) {
            float swapX = clipX0;
            float swapY = clipY0;
            clipX0 = clipX1;
            clipY0 = clipY1;
            clipX1 = swapX;
            clipY1 = swapY;
        }
        int first = Math.round(clipX0);
        int last = Math.round(clipX1);
        if (first == last) {
            if (depths[first] > distance) {
                DisplayList.add(lines, first, Math.round(clipY0), first, Math.round(clipY1), color);
            }
            return;
        }
        float slopeY = (clipY1 - clipY0) / (clipX1 - clipX0);
        int runStart = -1;
        for (int x = first; x <= last + 1; x++) {
            boolean visible = x <= last && depths[x] > distance;
            if (visible && runStart < 0) {
                runStart = x;
            } else if (!visible && runStart >= 0) {
                DisplayList.add(lines, runStart, Math.round(clipY0 + (runStart - clipX0) * slopeY),
                        x - 1, Math.round(clipY0 + (x - 1 - clipX0) * slopeY), color);
                runStart = -1;
            }
        }
    }

    /** Liang-Barsky clipping of a line to the view; the result is left in clipX0..clipY1. */
    private static boolean clip(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float enter = 0f;
        float leave = 1f;
        for (int edge = 0; edge < 4; edge++) {
            float p = edge == 0 ? -dx : edge == 1 ? dx : edge == 2 ? -dy : dy;
            float q = edge == 0 ? x0 : edge == 1 ? DisplayList.WIDTH - 1 - x0
                    : edge == 2 ? y0 - DisplayList.HEADER : DisplayList.HEIGHT - 1 - y0;
            if (p == 0) {
                if (q < 0) {
                    return false;
                }
            } else {
                float t = q / p;
                if (p < 0) {
                    enter = Math.max(enter, t);
                } else {
                    leave = Math.min(leave, t);
                }
            }
        }
        if (enter > leave) {
            return false;
        }
        clipX0 = Math.max(0f, Math.min(DisplayList.WIDTH - 1, x0 + dx * enter));
        clipY0 = Math.max(DisplayList.HEADER, Math.min(DisplayList.HEIGHT - 1, y0 + dy * enter));
        clipX1 = Math.max(0f, Math.min(DisplayList.WIDTH - 1, x0 + dx * leave));
        clipY1 = Math.max(DisplayList.HEADER, Math.min(DisplayList.HEIGHT - 1, y0 + dy * leave));
        return true;
    }

    /** The pistol, its muzzle flash, the crosshair and the red frame of a fresh wound, over the view. */
    private static void drawOverlay(short[] lines) {
        DisplayList.add(lines, CX - 3, CY, CX + 3, CY, CROSSHAIR);
        DisplayList.add(lines, CX, CY - 3, CX, CY + 3, CROSSHAIR);
        DisplayList.add(lines, 146, 239, 149, 213, GUN);
        DisplayList.add(lines, 149, 213, 155, 205, GUN);
        DisplayList.add(lines, 155, 205, 165, 205, GUN);
        DisplayList.add(lines, 165, 205, 171, 213, GUN);
        DisplayList.add(lines, 171, 213, 174, 239, GUN);
        DisplayList.add(lines, 156, 205, 156, 196, GUN);
        DisplayList.add(lines, 156, 196, 164, 196, GUN);
        DisplayList.add(lines, 164, 196, 164, 205, GUN);
        if (Weapon.flash > 0) {
            DisplayList.add(lines, 150, 186, 170, 186, Sprites.FLASH_COLOR);
            DisplayList.add(lines, 160, 178, 160, 193, Sprites.FLASH_COLOR);
            DisplayList.add(lines, 153, 180, 167, 192, Sprites.FLASH_COLOR);
            DisplayList.add(lines, 153, 192, 167, 180, Sprites.FLASH_COLOR);
        }
        if (Player.hurt > 0) {
            int top = DisplayList.HEADER + 1;
            int right = DisplayList.WIDTH - 1;
            int bottom = DisplayList.HEIGHT - 1;
            DisplayList.add(lines, 0, top, right, top, TftTouchShield.RED);
            DisplayList.add(lines, right, top, right, bottom, TftTouchShield.RED);
            DisplayList.add(lines, right, bottom, 0, bottom, TftTouchShield.RED);
            DisplayList.add(lines, 0, bottom, 0, top, TftTouchShield.RED);
        }
    }
}
