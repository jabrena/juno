package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * DOOM's renderer, drawing edges instead of textured columns. The BSP tree is walked front to back
 * from the marine's position; each wall seg is transformed into view space, clipped at the near
 * plane and projected. Every column keeps an open window between what has already been drawn above
 * and below it (DOOM's ceiling and floor clip arrays), so an edge is drawn only where it is not
 * hidden by something nearer: one-sided walls close their columns, and steps and lintels narrow
 * them. The walk stops as soon as every column is closed, and the depth at which each column closed is
 * left for {@link ThingRenderer} to hide the monsters and items standing behind its wall.
 */
final class Renderer {
    private static final float NEAR = 4f;
    static final float FOCAL = 160f;
    static final int CX = DisplayList.WIDTH / 2;
    static final int CY = (DisplayList.HEADER + DisplayList.HEIGHT) / 2;
    private static final int TOP = DisplayList.HEADER - 1;
    private static final int BOTTOM = DisplayList.HEIGHT;
    private static final int STACK = 64;

    static final int WALL = TftTouchShield.color(40, 255, 90);
    static final int WALL_FAR = TftTouchShield.color(20, 130, 50);
    static final int WALL_DISTANT = TftTouchShield.color(10, 70, 30);
    static final int STEP = TftTouchShield.color(255, 210, 40);
    static final int LINTEL = TftTouchShield.color(60, 200, 255);
    static final int DOOR = TftTouchShield.color(255, 60, 40);

    private static float viewX;
    private static float viewY;
    private static float viewZ;
    private static float cos;
    private static float sin;
    private static int closed;
    private static short[] depths;
    private static final int FAR = Short.MAX_VALUE;

    // The seg being drawn, in screen space.
    private static float leftX;
    private static float rightX;
    private static float leftDepth;
    private static float slope;
    private static int firstColumn;
    private static int lastColumn;

    private Renderer() {
    }

    /** Builds the frame seen from the marine's eye into the display list. */
    static void render(short[] lines, byte[] clips, short[] columnDepths, short[] stack, short[] ceilings,
                       short[] monsters, short[] shots, byte[] taken) {
        depths = columnDepths;
        viewX = Player.x;
        viewY = Player.y;
        viewZ = Player.eye;
        cos = (float) Math.cos(Player.angle);
        sin = (float) Math.sin(Player.angle);
        for (int x = 0; x < DisplayList.WIDTH; x++) {
            clips[x] = (byte) TOP;
            clips[DisplayList.WIDTH + x] = (byte) BOTTOM;
            depths[x] = (short) FAR;
        }
        closed = 0;
        DisplayList.begin();

        int count = LevelNodes.X.length;
        int depth = 0;
        stack[depth] = (short) (count == 0 ? 0x8000 : count - 1);
        depth = depth + 1;
        while (depth > 0 && closed < DisplayList.WIDTH) {
            depth = depth - 1;
            int child = stack[depth] & 0xFFFF;
            if ((child & 0x8000) != 0) {
                drawSubsector(lines, clips, ceilings, child & 0x7FFF);
            } else if (depth + 2 <= STACK) {
                boolean back = Player.onBackSide(child, viewX, viewY);
                int near = back ? LevelNodes.LEFT[child] : LevelNodes.RIGHT[child];
                int far = back ? LevelNodes.RIGHT[child] : LevelNodes.LEFT[child];
                stack[depth] = (short) far;
                stack[depth + 1] = (short) near;
                depth = depth + 2;
            }
        }
        ThingRenderer.draw(lines, depths, monsters, shots, taken);
        DisplayList.present(lines);
    }

    private static void drawSubsector(short[] lines, byte[] clips, short[] ceilings, int subsector) {
        int first = LevelNodes.SUBSECTOR_FIRST[subsector];
        int last = first + LevelNodes.SUBSECTOR_COUNT[subsector];
        for (int seg = first; seg < last && closed < DisplayList.WIDTH; seg++) {
            drawSeg(lines, clips, ceilings, seg);
        }
    }

    private static void drawSeg(short[] lines, byte[] clips, short[] ceilings, int seg) {
        int v1 = LevelSegs.V1[seg];
        int v2 = LevelSegs.V2[seg];
        float x1 = LevelVertices.X[v1] - viewX;
        float y1 = LevelVertices.Y[v1] - viewY;
        float x2 = LevelVertices.X[v2] - viewX;
        float y2 = LevelVertices.Y[v2] - viewY;
        float depth1 = x1 * cos + y1 * sin;
        float depth2 = x2 * cos + y2 * sin;
        if (depth1 < NEAR && depth2 < NEAR) {
            return;
        }
        float side1 = x1 * sin - y1 * cos;
        float side2 = x2 * sin - y2 * cos;
        int line = LevelSegs.LINE[seg];
        boolean startCorner = v1 == LevelLines.V1[line] || v1 == LevelLines.V2[line];
        boolean endCorner = v2 == LevelLines.V1[line] || v2 == LevelLines.V2[line];
        if (depth1 < NEAR) {
            float t = (NEAR - depth1) / (depth2 - depth1);
            side1 = side1 + (side2 - side1) * t;
            depth1 = NEAR;
            startCorner = false;
        } else if (depth2 < NEAR) {
            float t = (NEAR - depth2) / (depth1 - depth2);
            side2 = side2 + (side1 - side2) * t;
            depth2 = NEAR;
            endCorner = false;
        }
        leftX = CX + side1 * FOCAL / depth1;
        rightX = CX + side2 * FOCAL / depth2;
        if (leftX >= rightX) {
            return;
        }
        firstColumn = Math.max(0, (int) Math.ceil(leftX));
        lastColumn = Math.min(DisplayList.WIDTH - 1, (int) Math.ceil(rightX) - 1);
        if (firstColumn > lastColumn) {
            return;
        }
        leftDepth = 1f / depth1;
        slope = (1f / depth2 - leftDepth) / (rightX - leftX);
        startCorner = startCorner && leftX >= 0;
        endCorner = endCorner && rightX <= DisplayList.WIDTH;

        boolean backSide = LevelSegs.SIDE[seg] != 0;
        int front = backSide ? LevelLines.BACK[line] : LevelLines.FRONT[line];
        int back = backSide ? LevelLines.FRONT[line] : LevelLines.BACK[line];
        float frontFloor = Level.SECTOR_FLOOR[front] - viewZ;
        float frontCeiling = ceilings[front] - viewZ;
        int shade = shade(depth1 + depth2);
        if (back < 0) {
            edge(lines, clips, frontCeiling, shade);
            edge(lines, clips, frontFloor, shade);
            corners(lines, clips, frontCeiling, frontFloor, shade, startCorner, endCorner);
            closeColumns(clips);
            return;
        }
        float backFloor = Level.SECTOR_FLOOR[back] - viewZ;
        float backCeiling = ceilings[back] - viewZ;
        int color = isDoor(front) || isDoor(back) ? DOOR : STEP;
        if (backFloor != frontFloor) {
            edge(lines, clips, frontFloor, color);
            if (backFloor > frontFloor) {
                edge(lines, clips, backFloor, color);
                corners(lines, clips, backFloor, frontFloor, color, startCorner, endCorner);
            }
        }
        if (backCeiling != frontCeiling) {
            int lintel = color == DOOR ? DOOR : LINTEL;
            edge(lines, clips, frontCeiling, lintel);
            if (backCeiling < frontCeiling) {
                edge(lines, clips, backCeiling, lintel);
                corners(lines, clips, frontCeiling, backCeiling, lintel, startCorner, endCorner);
            }
        }
        narrowColumns(clips, Math.min(frontCeiling, backCeiling), Math.max(frontFloor, backFloor));
    }

    /** Screen row of a height (relative to the eye) at a column of the current seg. */
    private static int row(float height, int column) {
        float inverseDepth = leftDepth + slope * (column + 0.5f - leftX);
        return Math.round(CY - height * FOCAL * inverseDepth);
    }

    /** Draws the seg's edge at {@code height} wherever it shows between the columns' clip windows. */
    private static void edge(short[] lines, byte[] clips, float height, int color) {
        int runStart = -1;
        int runRow = 0;
        int previousRow = 0;
        for (int x = firstColumn; x <= lastColumn + 1; x++) {
            boolean visible = false;
            int y = 0;
            if (x <= lastColumn) {
                y = row(height, x);
                visible = y > (clips[x] & 0xFF) && y < (clips[DisplayList.WIDTH + x] & 0xFF);
            }
            if (visible && runStart < 0) {
                runStart = x;
                runRow = y;
            } else if (!visible && runStart >= 0) {
                DisplayList.add(lines, runStart, runRow, x - 1, previousRow, color);
                runStart = -1;
            }
            previousRow = y;
        }
    }

    /** Vertical edges at the seg's ends that are real wall corners, between two heights. */
    private static void corners(short[] lines, byte[] clips, float upper, float lower, int color,
                                boolean start, boolean end) {
        if (start) {
            corner(lines, clips, firstColumn, upper, lower, color);
        }
        if (end) {
            corner(lines, clips, lastColumn, upper, lower, color);
        }
    }

    private static void corner(short[] lines, byte[] clips, int column, float upper, float lower, int color) {
        int top = Math.max(row(upper, column), (clips[column] & 0xFF) + 1);
        int bottom = Math.min(row(lower, column), (clips[DisplayList.WIDTH + column] & 0xFF) - 1);
        if (top < bottom) {
            DisplayList.add(lines, column, top, column, bottom, color);
        }
    }

    private static void closeColumns(byte[] clips) {
        for (int x = firstColumn; x <= lastColumn; x++) {
            if ((clips[x] & 0xFF) + 1 < (clips[DisplayList.WIDTH + x] & 0xFF)) {
                close(clips, x);
            }
        }
    }

    private static void close(byte[] clips, int x) {
        clips[x] = (byte) BOTTOM;
        clips[DisplayList.WIDTH + x] = (byte) TOP;
        closed = closed + 1;
        float inverseDepth = leftDepth + slope * (x + 0.5f - leftX);
        depths[x] = (short) Math.min(FAR, Math.round(1f / Math.max(inverseDepth, 1f / FAR)));
    }

    /** What stays visible through a portal: below the lower of the two ceilings, above the higher floor. */
    private static void narrowColumns(byte[] clips, float ceiling, float floor) {
        for (int x = firstColumn; x <= lastColumn; x++) {
            int top = clips[x] & 0xFF;
            int bottom = clips[DisplayList.WIDTH + x] & 0xFF;
            if (top + 1 >= bottom) {
                continue;
            }
            top = Math.max(top, Math.min(BOTTOM, row(ceiling, x)));
            bottom = Math.min(bottom, Math.max(TOP, row(floor, x)));
            if (top + 1 >= bottom) {
                close(clips, x);
            } else {
                clips[x] = (byte) top;
                clips[DisplayList.WIDTH + x] = (byte) bottom;
            }
        }
    }

    private static boolean isDoor(int sector) {
        for (int d = 0; d < Level.DOOR_SECTOR.length; d++) {
            if (Level.DOOR_SECTOR[d] == sector) {
                return true;
            }
        }
        return false;
    }

    /** Nearer walls glow brighter, the way vector monitors fade with distance. */
    private static int shade(float depthSum) {
        if (depthSum < 1400f) {
            return WALL;
        }
        return depthSum < 3000f ? WALL_FAR : WALL_DISTANT;
    }
}
