package io.github.jabrena.juno.games.gladiator.view;

import io.github.jabrena.juno.games.gladiator.arena.Braziers;
import io.github.jabrena.juno.games.gladiator.arena.Coliseum;
import io.github.jabrena.juno.games.gladiator.arena.Javelins;
import io.github.jabrena.juno.games.gladiator.gladiator.Report;
import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;

/**
 * What stands on the sand, for a {@link View}: the bronze braziers and the smoke over those burning, the javelins
 * lying on the sand or in the air, and the lion, each drawn as straight lines between points of the arena and hidden
 * behind any nearer pillar.
 */
@SuppressWarnings("java:S1845")
public final class Props {
    public static final int SMOKE = 0x9494;
    public static final int LION = 0xFC00;
    /** Props farther than this are too small to draw. */
    private static final float PROP_FAR = 2200f;

    private Props() {
    }

    /** The lion, while it is out: body, legs, head and mane, and tail, turned the way it runs. */
    public static void lion(View view) {
        Report report = view.report();
        float x = report.lionX();
        float y = report.lionY();
        float depth = (x - view.viewX()) * view.cos() + (y - view.viewY()) * view.sin();
        if (depth < -100f || depth > PROP_FAR) {
            return;
        }
        float fx = (float) Math.cos(report.lionAngle());
        float fy = (float) Math.sin(report.lionAngle());
        limb(view, x, y, fx, fy, -45, 0, 85, 40, 0, 90);
        limb(view, x, y, fx, fy, -45, 0, 45, 40, 0, 50);
        limb(view, x, y, fx, fy, -45, 0, 45, -45, 0, 85);
        limb(view, x, y, fx, fy, 40, 0, 50, 40, 0, 90);
        for (int side = -15; side <= 15; side += 30) {
            limb(view, x, y, fx, fy, 35, side, 50, 35, side, 0);
            limb(view, x, y, fx, fy, -40, side, 45, -40, side, 0);
        }
        limb(view, x, y, fx, fy, 55, 0, 85, 55, 0, 112);
        limb(view, x, y, fx, fy, 55, 0, 112, 78, 0, 104);
        limb(view, x, y, fx, fy, 78, 0, 104, 78, 0, 82);
        limb(view, x, y, fx, fy, 78, 0, 82, 55, 0, 85);
        limb(view, x, y, fx, fy, -45, 0, 75, -82, 0, 40);
        ring(view, x + fx * 55, y + fy * 55, 30f, 98f, 8, LION);
    }

    /**
     * A line of the lion's figure at ({@code x}, {@code y}), facing along the unit vector ({@code fx}, {@code fy}):
     * from ({@code f0} ahead, {@code s0} aside, {@code h0} up) to the second point.
     */
    private static void limb(View view, float x, float y, float fx, float fy, float f0, float s0, float h0,
                             float f1, float s1, float h1) {
        worldLine(view, x + fx * f0 + fy * s0, y + fy * f0 - fx * s0, h0,
                x + fx * f1 + fy * s1, y + fy * f1 - fx * s1, h1, LION);
    }

    /** The braziers, the smoke over those burning, and the javelins on the sand or in the air. */
    public static void draw(View view) {
        Braziers braziers = view.coliseum().braziers();
        for (int b = 0; Braziers.ENABLED && b < Braziers.COUNT; b++) {
            float depth = (Braziers.X[b] - view.viewX()) * view.cos() + (Braziers.Y[b] - view.viewY()) * view.sin();
            if (depth < -Braziers.SMOKE || depth > PROP_FAR) {
                continue;
            }
            brazier(view, Braziers.X[b], Braziers.Y[b], braziers.smoky(b));
            if (braziers.smoky(b)) {
                ring(view, Braziers.X[b], Braziers.Y[b], Braziers.SMOKE * 0.75f, 30f, 8, SMOKE);
                ring(view, Braziers.X[b], Braziers.Y[b], Braziers.SMOKE, 150f, 4, SMOKE);
                ring(view, Braziers.X[b], Braziers.Y[b], Braziers.SMOKE * 0.7f, 280f, 8, SMOKE);
            }
        }
        Report report = view.report();
        for (int j = 0; j < Javelins.COUNT; j++) {
            int known = report.javelin(j);
            if (known == GladiatorProtocol.JAVELIN_GROUND || known == GladiatorProtocol.JAVELIN_FLYING) {
                javelin(view, report.javelinX(j), report.javelinY(j), report.javelinAngle(j),
                        known == GladiatorProtocol.JAVELIN_FLYING);
            }
        }
    }

    /** A javelin in the air, or stuck in the sand, leaning a little: tall enough to be seen from across the arena. */
    private static void javelin(View view, float x, float y, float angle, boolean flying) {
        float dx = (float) Math.cos(angle) * 70f;
        float dy = (float) Math.sin(angle) * 70f;
        if (flying) {
            worldLine(view, x - 2 * dx, y - 2 * dy, 150f, x, y, 140f, Figure.BLADE);
            return;
        }
        float leanX = dx * 0.4f;
        float leanY = dy * 0.4f;
        worldLine(view, x, y, 0f, x + leanX, y + leanY, 170f, View.BRONZE);
        worldLine(view, x + leanX, y + leanY, 170f, x + leanX * 1.2f, y + leanY * 1.2f, 200f, Figure.BLADE);
    }

    /** A bronze bowl on three legs, with flames in it while it smokes. */
    private static void brazier(View view, float x, float y, boolean burning) {
        for (int leg = 0; leg < Coliseum.WALL_SEGMENTS; leg += Coliseum.WALL_SEGMENTS / 3 + 1) {
            worldLine(view, x + 22f * Coliseum.CORNER_COS[leg] / Coliseum.UNIT,
                    y + 22f * Coliseum.CORNER_SIN[leg] / Coliseum.UNIT, 0f, x, y, 80f, View.BRONZE);
        }
        ring(view, x, y, 28f, 80f, 8, View.BRONZE);
        if (burning) {
            worldLine(view, x - 12f, y, 84f, x, y, 130f, View.WOUND);
            worldLine(view, x + 12f, y, 84f, x, y + 6f, 115f, View.WOUND);
        }
    }

    /** A ring of {@code Coliseum.WALL_SEGMENTS / step} straight pieces round ({@code x}, {@code y}). */
    private static void ring(View view, float x, float y, float radius, float height, int step, int color) {
        for (int i = 0; i < Coliseum.WALL_SEGMENTS; i += step) {
            int j = (i + step) % Coliseum.WALL_SEGMENTS;
            worldLine(view, x + radius * Coliseum.CORNER_COS[i] / Coliseum.UNIT,
                    y + radius * Coliseum.CORNER_SIN[i] / Coliseum.UNIT, height,
                    x + radius * Coliseum.CORNER_COS[j] / Coliseum.UNIT,
                    y + radius * Coliseum.CORNER_SIN[j] / Coliseum.UNIT, height, color);
        }
    }

    /**
     * A straight line between two points of the arena, {@code h0} and {@code h1} above the sand, cut at the eye's
     * near plane and hidden wherever a nearer pillar stands.
     */
    public static void worldLine(View view, float x0, float y0, float h0, float x1, float y1, float h1, int color) {
        float ax = x0 - view.viewX();
        float ay = y0 - view.viewY();
        float bx = x1 - view.viewX();
        float by = y1 - view.viewY();
        float depthA = ax * view.cos() + ay * view.sin();
        float depthB = bx * view.cos() + by * view.sin();
        if (depthA < View.NEAR && depthB < View.NEAR) {
            return;
        }
        float sideA = ax * view.sin() - ay * view.cos();
        float sideB = bx * view.sin() - by * view.cos();
        float heightA = h0;
        float heightB = h1;
        if (depthA < View.NEAR) {
            float t = (View.NEAR - depthA) / (depthB - depthA);
            sideA = sideA + (sideB - sideA) * t;
            heightA = heightA + (heightB - heightA) * t;
            depthA = View.NEAR;
        } else if (depthB < View.NEAR) {
            float t = (View.NEAR - depthB) / (depthA - depthB);
            sideB = sideB + (sideA - sideB) * t;
            heightB = heightB + (heightA - heightB) * t;
            depthB = View.NEAR;
        }
        float scaleA = View.FOCAL / depthA;
        float scaleB = View.FOCAL / depthB;
        view.billboard().line(View.CX + sideA * scaleA, View.CY - (heightA - Coliseum.EYE) * scaleA,
                View.CX + sideB * scaleB, View.CY - (heightB - Coliseum.EYE) * scaleB, (depthA + depthB) / 2f, color);
    }
}
