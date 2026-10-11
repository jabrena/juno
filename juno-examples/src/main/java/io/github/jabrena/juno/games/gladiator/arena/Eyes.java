package io.github.jabrena.juno.games.gladiator.arena;

/**
 * What a gladiator sees: another one is in sight only when it stands inside the field of view, a little wider than
 * the screen's, and no pillar or cloud of smoke of the {@link Coliseum} is in the way. The arena judges sight with
 * this before telling a gladiator where its opponent is, and to decide whether a strike came as a surprise.
 */
public final class Eyes {
    /** Half the field of view: 50 degrees either side, against the screen's 45. */
    public static final float HALF_VIEW = 0.873f;
    public static final float PI = (float) Math.PI;

    private Eyes() {
    }

    /** Whether a gladiator at ({@code x}, {@code y}) facing {@code angle} sees the point ({@code tx}, {@code ty}). */
    public static boolean sees(Coliseum coliseum, float x, float y, float angle, float tx, float ty) {
        return sees(coliseum, x, y, angle, tx, ty, HALF_VIEW);
    }

    /** The same, for eyes that see {@code halfView} radians either side, such as a SECUTOR's in its helmet. */
    public static boolean sees(Coliseum coliseum, float x, float y, float angle, float tx, float ty, float halfView) {
        float bearing = (float) Math.atan2(ty - y, tx - x);
        if (Math.abs(wrap(bearing - angle)) > halfView) {
            return false;
        }
        return coliseum.clearSight(x, y, tx, ty);
    }

    /** {@code angle} brought into -pi..pi. */
    public static float wrap(float angle) {
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
