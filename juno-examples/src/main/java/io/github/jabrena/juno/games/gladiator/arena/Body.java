package io.github.jabrena.juno.games.gladiator.arena;

/**
 * A gladiator's own body, moved by its board: it turns, walks and slides along the wall and round the pillars of its
 * {@link Coliseum}, and keeps a body's width from an opponent it can see. The arena takes where it stands from
 * {@link GladiatorProtocol#STATE}. The lion runs on one too.
 */
public final class Body {
    /**
     * Headings, as turns off straight ahead, that a blocked walk tries in turn to slide round what is in the way: up
     * to 90 degrees either side, since a body touching a round pillar can only slide along its tangent.
     */
    private static final float[] SLIDE = {0f, 0.35f, -0.35f, 0.75f, -0.75f, 1.2f, -1.2f, 1.57f, -1.57f};

    private final Coliseum coliseum;
    private float x;
    private float y;
    private float angle;
    /** The opponent's position when in sight, so the two never walk into each other. */
    private boolean opponentInSight;
    private float opponentX;
    private float opponentY;

    public Body(Coliseum coliseum) {
        this.coliseum = coliseum;
    }

    public Coliseum coliseum() {
        return coliseum;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float angle() {
        return angle;
    }

    /** Stands at ({@code x}, {@code y}) facing {@code angle}. */
    public void place(float x, float y, float angle) {
        this.x = x;
        this.y = y;
        this.angle = angle;
    }

    /** Back to the gate of {@code slot}. */
    public void spawn(int slot) {
        place(Coliseum.spawnX(slot), 0f, Coliseum.spawnAngle(slot));
        opponentInSight = false;
    }

    /** Where the opponent stands, when {@code inSight}, so the body keeps clear of it. */
    public void seeOpponent(boolean inSight, float x, float y) {
        opponentInSight = inSight;
        opponentX = x;
        opponentY = y;
    }

    public void turn(float by) {
        angle = Eyes.wrap(angle + by);
    }

    /** Turns to face {@code angle} at once. */
    public void face(float angle) {
        this.angle = angle;
    }

    /**
     * Walks {@code distance} forward (backward when negative), sliding round a pillar or along the wall when the way
     * ahead is blocked: it takes the first heading of {@link #SLIDE} that is free. Returns how far it got along
     * the way it faces.
     */
    public float walk(float distance) {
        for (int i = 0; i < SLIDE.length; i++) {
            float turn = SLIDE[i];
            float heading = angle + turn;
            float dx = (float) Math.cos(heading) * distance;
            float dy = (float) Math.sin(heading) * distance;
            if (free(x + dx, y + dy)) {
                x = x + dx;
                y = y + dy;
                return Math.abs(distance) * (float) Math.cos(turn);
            }
        }
        return 0f;
    }

    public float distanceTo(float tx, float ty) {
        return (float) Math.hypot(tx - x, ty - y);
    }

    private boolean free(float nx, float ny) {
        if (!coliseum.walkable(nx, ny)) {
            return false;
        }
        if (!opponentInSight) {
            return true;
        }
        float dx = nx - opponentX;
        float dy = ny - opponentY;
        float close = 2 * Coliseum.BODY;
        // Moving away from an opponent already too close is always allowed.
        return dx * dx + dy * dy >= close * close
                || dx * dx + dy * dy > (x - opponentX) * (x - opponentX) + (y - opponentY) * (y - opponentY);
    }
}
