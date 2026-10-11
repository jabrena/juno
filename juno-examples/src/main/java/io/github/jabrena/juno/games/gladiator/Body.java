package io.github.jabrena.juno.games.gladiator;

/**
 * A gladiator's own body, moved by its board: it turns, walks and slides along the wall and round the pillars,
 * and keeps a body's width from an opponent it can see. The arena takes where it stands from {@link
 * GladiatorProtocol#STATE}.
 */
final class Body {
    float x;
    float y;
    float angle;
    /** The opponent's position when in sight, so the two never walk into each other. */
    boolean opponentInSight;
    float opponentX;
    float opponentY;

    /** Back to the gate of {@code slot}. */
    void spawn(int slot) {
        x = Coliseum.spawnX(slot);
        y = 0;
        angle = Coliseum.spawnAngle(slot);
        opponentInSight = false;
    }

    void turn(float by) {
        angle = Eyes.wrap(angle + by);
    }

    /** Headings, as turns off straight ahead, that a blocked walk tries in turn to slide round what is in the way. */
    private static final float[] SLIDE = {0f, 0.35f, -0.35f, 0.75f, -0.75f, 1.2f, -1.2f};

    /**
     * Walks {@code distance} forward (backward when negative), sliding round a pillar or along the wall when the way
     * ahead is blocked: it takes the first heading of {@link #SLIDE} that is free. Returns how far it got along
     * the way it faces.
     */
    float walk(float distance) {
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

    float distanceTo(float tx, float ty) {
        return (float) Math.hypot(tx - x, ty - y);
    }

    private boolean free(float nx, float ny) {
        if (!Coliseum.walkable(nx, ny)) {
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
