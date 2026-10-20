package io.github.jabrena.juno.games.gladiator.view;

import io.github.jabrena.juno.games.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.thirdparty.tft.TftTouchShield;

/** A rotating 3D line model with class-specific helmet, shield and weapons, built into a {@link ViewList}. */
public final class ClassWireframe {
    private static final int CENTER_X = 94;
    private static final int GROUND_Y = 194;
    private static final float FOCAL = 235f;
    private static final float DISTANCE = 285f;
    private static final int ARMOR = 0xB5B5;
    private static final int LEATHER = 0xA145;
    private static final int GOLD = 0xE6C0;

    private final ViewList list;
    private float cosine;
    private float sine;
    private int centerX;
    private int groundY;
    private float focal;
    private float distance;

    public ClassWireframe(ViewList list) {
        this.list = list;
    }

    public void build(int kind, float angle) {
        list.begin();
        projection(CENTER_X, GROUND_Y, FOCAL, DISTANCE, angle);
        body(kind);
        equipment(kind);
    }

    /** Builds the player and CPU models together for the Solo versus screen. */
    public void buildVersus(int playerKind, int opponentKind, float angle) {
        list.begin();
        projection(80, 199, 175f, 300f, angle);
        body(playerKind);
        equipment(playerKind);
        projection(240, 199, 175f, 300f, -angle);
        body(opponentKind);
        equipment(opponentKind);
    }

    private void projection(int x, int y, float scale, float depth, float angle) {
        centerX = x;
        groundY = y;
        focal = scale;
        distance = depth;
        cosine = (float) Math.cos(angle);
        sine = (float) Math.sin(angle);
    }

    private void body(int kind) {
        // Torso, belt and skirt have depth: their side edges make the rotation visible.
        box(-21, 72, -10, 21, 121, 10, ARMOR);
        line(-22, 70, -10, 22, 70, -10, LEATHER);
        line(-22, 70, 10, 22, 70, 10, LEATHER);
        line(-22, 70, -10, -14, 55, -8, LEATHER);
        line(22, 70, -10, 14, 55, -8, LEATHER);
        line(-14, 55, -8, 14, 55, -8, LEATHER);

        // Legs and feet.
        limb(-12, 55, 0, -17, 28, 5, -20, 1, 8);
        limb(12, 55, 0, 17, 28, -5, 20, 1, 8);
        line(-20, 1, 8, -31, 1, -1, ARMOR);
        line(20, 1, 8, 31, 1, -1, ARMOR);

        // Arms finish at the two equipment grips.
        limb(-21, 113, 0, -35, 91, 8, -35, 68, 12);
        limb(21, 113, 0, 35, 91, -8, 35, 68, -12);

        if (kind == GladiatorClass.RETIARIUS) {
            head(false, false);
        } else if (kind == GladiatorClass.SECUTOR) {
            head(true, false);
        } else {
            head(true, true);
        }
    }

    private void head(boolean helmet, boolean crest) {
        int color = helmet ? ARMOR : 0xE3AA;
        box(-12, 124, -9, 12, 149, 9, color);
        if (helmet) {
            line(-12, 137, -10, 12, 137, -10, View.WALL);
            line(0, 124, -10, 0, 149, -10, View.WALL);
        }
        if (crest) {
            line(-12, 151, 0, 0, 166, 0, TftTouchShield.RED);
            line(0, 166, 0, 13, 151, 0, TftTouchShield.RED);
        }
    }

    private void equipment(int kind) {
        if (kind == GladiatorClass.RETIARIUS) {
            trident();
            net();
            return;
        }
        if (kind == GladiatorClass.THRAEX) {
            sica();
            shield(-38, 52, 17, 20, 58, TftTouchShield.YELLOW);
            return;
        }
        gladius();
        // The scutum is a tall body shield. Its points still turn with the complete 3D model.
        shield(-43, 42, 22, 24, 92, kind == GladiatorClass.SECUTOR ? TftTouchShield.RED : TftTouchShield.ORANGE);
    }

    private void gladius() {
        line(35, 68, -12, 47, 127, -10, Figure.BLADE);
        line(30, 76, -12, 43, 73, -12, GOLD);
        line(47, 127, -10, 43, 119, -10, Figure.BLADE);
        line(47, 127, -10, 50, 118, -10, Figure.BLADE);
    }

    private void sica() {
        line(35, 68, -12, 48, 116, -9, Figure.BLADE);
        line(48, 116, -9, 42, 137, -5, Figure.BLADE);
        line(42, 137, -5, 35, 131, -3, Figure.BLADE);
        line(30, 75, -12, 43, 72, -12, GOLD);
    }

    private void trident() {
        line(35, 68, -12, 45, 151, -8, Figure.BLADE);
        line(45, 151, -8, 45, 169, -8, Figure.BLADE);
        line(45, 151, -8, 35, 166, -8, Figure.BLADE);
        line(45, 151, -8, 55, 166, -8, Figure.BLADE);
    }

    private void net() {
        line(-35, 68, 12, -58, 94, 15, TftTouchShield.CYAN);
        line(-58, 94, 15, -39, 118, 20, TftTouchShield.CYAN);
        line(-39, 118, 20, -20, 94, 18, TftTouchShield.CYAN);
        line(-20, 94, 18, -35, 68, 12, TftTouchShield.CYAN);
        line(-49, 83, 14, -29, 106, 19, TftTouchShield.CYAN);
        line(-56, 103, 17, -27, 86, 16, TftTouchShield.CYAN);
    }

    private void shield(int x, int y, int z, int halfWidth, int height, int color) {
        line(x - halfWidth, y, z, x + halfWidth, y, z, color);
        line(x + halfWidth, y, z, x + halfWidth, y + height, z, color);
        line(x + halfWidth, y + height, z, x - halfWidth, y + height, z, color);
        line(x - halfWidth, y + height, z, x - halfWidth, y, z, color);
        line(x, y, z, x, y + height, z, color);
        line(x - halfWidth, y + height / 2, z, x + halfWidth, y + height / 2, z, color);
        line(x - halfWidth, y, z - 4, x - halfWidth, y + height, z - 4, ARMOR);
        line(x + halfWidth, y, z - 4, x + halfWidth, y + height, z - 4, ARMOR);
    }

    private void limb(float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2) {
        line(x0, y0, z0, x1, y1, z1, ARMOR);
        line(x1, y1, z1, x2, y2, z2, ARMOR);
    }

    private void box(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        line(x0, y0, z0, x1, y0, z0, color);
        line(x1, y0, z0, x1, y1, z0, color);
        line(x1, y1, z0, x0, y1, z0, color);
        line(x0, y1, z0, x0, y0, z0, color);
        line(x0, y0, z1, x1, y0, z1, color);
        line(x1, y0, z1, x1, y1, z1, color);
        line(x1, y1, z1, x0, y1, z1, color);
        line(x0, y1, z1, x0, y0, z1, color);
        line(x0, y0, z0, x0, y0, z1, color);
        line(x1, y0, z0, x1, y0, z1, color);
        line(x1, y1, z0, x1, y1, z1, color);
        line(x0, y1, z0, x0, y1, z1, color);
    }

    private void line(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float rx0 = x0 * cosine - z0 * sine;
        float rz0 = distance + x0 * sine + z0 * cosine;
        float rx1 = x1 * cosine - z1 * sine;
        float rz1 = distance + x1 * sine + z1 * cosine;
        int px0 = centerX + Math.round(rx0 * focal / rz0);
        int py0 = groundY - Math.round(y0 * focal / rz0);
        int px1 = centerX + Math.round(rx1 * focal / rz1);
        int py1 = groundY - Math.round(y1 * focal / rz1);
        list.add(px0, py0, px1, py1, color);
    }
}
