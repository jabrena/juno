package io.github.jabrena.juno.games.doom;

/**
 * Things drawn after the walls: items, monsters, corpses and fireballs as billboards that always face
 * the marine, each line kept only in the columns where it stands nearer than the wall {@link Renderer}
 * closed that column with; then the muzzle flash and the red frame of a fresh wound over the view. The
 * pistol and the crosshair never change, so they are added before the walls, into the display list's
 * pinned overlay ({@link #drawGun}).
 */
final class ThingRenderer {
    private static final int NEAREST_THING = 16;
    private static final float FOCAL = Renderer.FOCAL;
    private static final int CX = Renderer.CX;
    private static final int CY = Renderer.CY;
    // One repeated byte each, like the walls' colors (see Renderer): cheap to send to the screen.
    private static final int GUN = 0xB5B5;
    private static final int CROSSHAIR = 0x4B4B;
    private static final int WOUND = 0xE0E0;

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
    // The billboard being drawn, once projected.
    private static float scale;
    private static float centerX;
    private static int distance;

    private ThingRenderer() {
    }

    static void draw(short[] lines, short[] columnDepths, short[] monsters, short[] shots, byte[] taken) {
        depths = columnDepths;
        viewX = Player.x;
        viewY = Player.y;
        viewZ = Player.eye;
        cos = Renderer.cos;
        sin = Renderer.sin;
        drawItems(lines, taken);
        drawThings(lines, monsters, shots);
        drawEffects(lines);
    }

    private static void drawItems(short[] lines, byte[] taken) {
        for (int i = 0; i < World.items; i++) {
            if (taken[i] == 0) {
                float x = World.itemX[i];
                float y = World.itemY[i];
                // Only an item in view needs its floor, which takes a walk down the BSP tree.
                if (project(x, y)) {
                    int kind = World.itemKind[i];
                    strokes(lines, Sprites.itemShape(kind), World.sectorFloor[Player.sectorAt(x, y)],
                            Sprites.itemColor(kind));
                }
            }
        }
    }

    private static void drawThings(short[] lines, short[] monsters, short[] shots) {
        for (int i = 0; i < World.monsters; i++) {
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
        if (project(wx, wy)) {
            strokes(lines, shape, baseZ, color);
        }
    }

    /** Projects a billboard standing at (wx, wy); returns whether it is far enough ahead and near the view. */
    private static boolean project(float wx, float wy) {
        float x = wx - viewX;
        float y = wy - viewY;
        float depth = x * cos + y * sin;
        if (depth < NEAREST_THING) {
            return false;
        }
        scale = FOCAL / depth;
        centerX = CX + (x * sin - y * cos) * scale;
        distance = Math.round(depth);
        return centerX >= -40 * scale && centerX <= DisplayList.WIDTH + 40 * scale;
    }

    /** The projected billboard's lines, with its origin at height {@code baseZ}. */
    private static void strokes(short[] lines, int shape, float baseZ, int color) {
        float baseY = CY - (baseZ - viewZ) * scale;
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
                    : edge == 2 ? y0 - DisplayList.VIEW_TOP : DisplayList.VIEW_BOTTOM - 1 - y0;
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
        clipY0 = Math.max(DisplayList.VIEW_TOP, Math.min(DisplayList.VIEW_BOTTOM - 1, y0 + dy * enter));
        clipX1 = Math.max(0f, Math.min(DisplayList.WIDTH - 1, x0 + dx * leave));
        clipY1 = Math.max(DisplayList.VIEW_TOP, Math.min(DisplayList.VIEW_BOTTOM - 1, y0 + dy * leave));
        return true;
    }

    /** The crosshair and the pistol: the same lines every frame. */
    static void drawGun(short[] lines) {
        DisplayList.add(lines, CX - 3, CY, CX + 3, CY, CROSSHAIR);
        DisplayList.add(lines, CX, CY - 3, CX, CY + 3, CROSSHAIR);
        DisplayList.add(lines, 146, 199, 149, 173, GUN);
        DisplayList.add(lines, 149, 173, 155, 165, GUN);
        DisplayList.add(lines, 155, 165, 165, 165, GUN);
        DisplayList.add(lines, 165, 165, 171, 173, GUN);
        DisplayList.add(lines, 171, 173, 174, 199, GUN);
        DisplayList.add(lines, 156, 165, 156, 156, GUN);
        DisplayList.add(lines, 156, 156, 164, 156, GUN);
        DisplayList.add(lines, 164, 156, 164, 165, GUN);
    }

    /** The muzzle flash and the red frame of a fresh wound, over the view. */
    private static void drawEffects(short[] lines) {
        if (Weapon.flash > 0) {
            DisplayList.add(lines, 150, 146, 170, 146, Sprites.FLASH_COLOR);
            DisplayList.add(lines, 160, 138, 160, 153, Sprites.FLASH_COLOR);
            DisplayList.add(lines, 153, 140, 167, 152, Sprites.FLASH_COLOR);
            DisplayList.add(lines, 153, 152, 167, 140, Sprites.FLASH_COLOR);
        }
        if (Player.hurt > 0) {
            int top = DisplayList.VIEW_TOP + 1;
            int right = DisplayList.WIDTH - 1;
            int bottom = DisplayList.VIEW_BOTTOM - 1;
            DisplayList.add(lines, 0, top, right, top, WOUND);
            DisplayList.add(lines, right, top, right, bottom, WOUND);
            DisplayList.add(lines, right, bottom, 0, bottom, WOUND);
            DisplayList.add(lines, 0, bottom, 0, top, WOUND);
        }
    }
}
