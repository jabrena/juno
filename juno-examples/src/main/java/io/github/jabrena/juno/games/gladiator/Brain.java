package io.github.jabrena.juno.games.gladiator;

/**
 * The CPU gladiator: its memory of what its eyes saw and the steering that walks it round the pillars, shared by
 * the two behaviours, {@link Aggressive} and {@link Ambush}. It only knows where the opponent is while the arena says
 * it is in sight; after that it remembers where it saw it last. Each frame {@link #drive} decides how far to turn
 * and walk, and whether to swing the gladius.
 */
final class Brain {
    static final int AGGRESSIVE = 1;
    static final int AMBUSH = 2;

    /** What the gladiator is doing, shown on the arena's screen. */
    static final int WAITING = 0;
    static final int SEARCHING = 1;
    static final int HUNTING = 2;
    static final int HIDING = 3;
    static final int LURKING = 4;
    static final int CHARGING = 5;

    /** Radians a frame. */
    static final float TURN = 0.14f;
    /** Units a frame, at 25 frames a second. */
    static final float RUN = 6f;
    static final float WALK = 4.5f;
    static final float CREEP = 3f;
    /** A little short of the arena's reach, as the opponent's position is a tenth of a second old. */
    static final int STRIKE_RANGE = Match.REACH - 14;
    static final float STRIKE_AIM = 0.3f;
    static final int HURT_MILLIS = 1500;
    private static final int STUCK_FRAMES = 20;

    final int behaviour;
    int slot;
    int mode;

    boolean sees;
    boolean everSeen;
    float seenX;
    float seenY;
    int seenAt;
    int hurtAt;

    float turn;
    float walk;
    boolean strike;
    /** Frames in a row that a walk got (almost) nowhere. */
    int stuck;

    // Aggressive: the patrol and the look round at each of its points.
    int waypoint;
    int lookUntil;
    // Ambush: the pillar it hides behind, where it stands and where it watches.
    int pillar;
    float hideX;
    float hideY;
    float threatX;
    float threatY;
    int lurkSince;

    Brain(int behaviour) {
        this.behaviour = behaviour;
    }

    /** Forgets everything, for a new round from the gate of {@code slot}. */
    void reset(int slot, int now) {
        this.slot = slot;
        mode = behaviour == AMBUSH ? HIDING : SEARCHING;
        sees = false;
        everSeen = false;
        hurtAt = now - 10 * HURT_MILLIS;
        stuck = 0;
        waypoint = -1;
        lookUntil = now;
        pillar = -1;
        threatX = Coliseum.spawnX(1 - slot);
        threatY = 0;
        lurkSince = now;
    }

    /** What the eyes report this frame: the opponent at ({@code x}, {@code y}) when {@code visible}. */
    void see(boolean visible, float x, float y, int now) {
        sees = visible;
        if (visible) {
            everSeen = true;
            seenX = x;
            seenY = y;
            seenAt = now;
        }
    }

    /** A blow landed on this gladiator. */
    void hurt(int now) {
        hurtAt = now;
    }

    boolean recentlyHurt(int now) {
        return now - hurtAt < HURT_MILLIS;
    }

    /** Decides this frame's move and makes it. */
    void drive(Body body, int now) {
        turn = 0f;
        walk = 0f;
        strike = false;
        if (behaviour == AMBUSH) {
            Ambush.think(this, body, now);
        } else {
            Aggressive.think(this, body, now);
        }
        body.turn(turn);
        float moved = walk > 0f ? body.walk(walk) : 0f;
        stuck = walk > 0f && moved < walk * 0.3f ? stuck + 1 : 0;
    }

    boolean isStuck() {
        return stuck > STUCK_FRAMES;
    }

    /** Closes in on the opponent in sight and swings when it is in reach and in front. */
    void fight(Body body) {
        float off = face(body, seenX, seenY);
        float distance = body.distanceTo(seenX, seenY);
        if (distance > STRIKE_RANGE - 6 && off < 0.9f) {
            walk = RUN;
        }
        strike = distance <= STRIKE_RANGE && off < STRIKE_AIM;
    }

    /** Turns toward the point, at most {@link #TURN}; returns how far off it still faces, in radians. */
    float face(Body body, float tx, float ty) {
        float off = Eyes.wrap((float) Math.atan2(ty - body.y, tx - body.x) - body.angle);
        turn = Math.clamp(off, -TURN, TURN);
        return Math.abs(off - turn);
    }

    /** Heads for the point at {@code speed}, round the side of the first pillar in the way. */
    void goTo(Body body, float tx, float ty, float speed) {
        float aimX = tx;
        float aimY = ty;
        int p = Coliseum.blocking(body.x, body.y, tx, ty, Coliseum.BODY + 4);
        if (p >= 0 && Coliseum.pillarAt(tx, ty, Coliseum.BODY + 4) != p) {
            float dx = tx - body.x;
            float dy = ty - body.y;
            float length = Math.max(1f, (float) Math.hypot(dx, dy));
            // The perpendicular to the way, on the side of the pillar it is already nearer to.
            float nx = -dy / length;
            float ny = dx / length;
            float side = (Coliseum.pillarX[p] - body.x) * nx + (Coliseum.pillarY[p] - body.y) * ny;
            float around = Coliseum.pillarRadius + Coliseum.BODY + 20;
            float sign = side > 0f ? -1f : 1f;
            aimX = Coliseum.pillarX[p] + nx * around * sign;
            aimY = Coliseum.pillarY[p] + ny * around * sign;
        }
        float off = face(body, aimX, aimY);
        walk = off < 1.0f ? speed : speed * 0.3f;
    }
}
