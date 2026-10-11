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

    /** Walks {@code distance} forward (backward when negative); returns how far it got. */
    float walk(float distance) {
        float dx = (float) Math.cos(angle) * distance;
        float dy = (float) Math.sin(angle) * distance;
        if (free(x + dx, y + dy)) {
            x = x + dx;
            y = y + dy;
            return Math.abs(distance);
        }
        // Slide along whatever is in the way: try each axis on its own.
        if (free(x + dx, y)) {
            x = x + dx;
            return Math.abs(dx);
        }
        if (free(x, y + dy)) {
            y = y + dy;
            return Math.abs(dy);
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
