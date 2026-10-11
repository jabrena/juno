package io.github.jabrena.juno.games.gladiator;

/**
 * What a gladiator sees: another one is in sight only when it stands inside the field of view, a little wider than
 * the screen's, and no pillar is in the way. The arena judges sight with this before telling a gladiator where its
 * opponent is, and to decide whether a strike came as a surprise.
 */
final class Eyes {
    /** Half the field of view: 50 degrees either side, against the screen's 45. */
    static final float HALF_VIEW = 0.873f;
    static final float PI = (float) Math.PI;

    private Eyes() {
    }

    /** Whether a gladiator at ({@code x}, {@code y}) facing {@code angle} sees the point ({@code tx}, {@code ty}). */
    static boolean sees(float x, float y, float angle, float tx, float ty) {
        float bearing = (float) Math.atan2(ty - y, tx - x);
        if (Math.abs(wrap(bearing - angle)) > HALF_VIEW) {
            return false;
        }
        return Coliseum.clearSight(x, y, tx, ty);
    }

    /** {@code angle} brought into -pi..pi. */
    static float wrap(float angle) {
        float wrapped = angle;
        while (wrapped > PI) {
            wrapped = wrapped - 2 * PI;
        }
        while (wrapped < -PI) {
            wrapped = wrapped + 2 * PI;
        }
        return wrapped;
    }
}
