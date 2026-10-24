package io.github.jabrena.juno.games.gladiator.gladiator;

import io.github.jabrena.juno.games.gladiator.arena.Arms;
import io.github.jabrena.juno.games.gladiator.arena.Body;
import io.github.jabrena.juno.games.gladiator.arena.Coliseum;
import io.github.jabrena.juno.games.gladiator.arena.Eyes;
import io.github.jabrena.juno.games.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.games.gladiator.screen.Opening;

/**
 * The CPU gladiator: its memory of what its eyes saw and the steering that walks it round the pillars, shared by
 * the two behaviours, {@link Aggressive} and {@link Ambush}. It only knows where the opponent is while the arena says
 * it is in sight; after that it remembers where it saw it last. Each frame {@link #drive} decides how far to turn
 * and walk, and whether to swing the gladius; its {@link Senses} decide about javelin and net.
 */
public final class Brain {
    public static final int AGGRESSIVE = 1;
    public static final int AMBUSH = 2;

    /** What the gladiator is doing, shown on the arena's screen. */
    public static final int WAITING = 0;
    public static final int SEARCHING = 1;
    public static final int HUNTING = 2;
    public static final int HIDING = 3;
    public static final int LURKING = 4;
    public static final int CHARGING = 5;
    public static final int FLEEING = 6;

    /** Radians a frame. */
    public static final float TURN = 0.14f;
    /** Units a frame, at 25 frames a second. */
    public static final float RUN = 6f;
    public static final float WALK = 4.5f;
    public static final float CREEP = 3f;
    /**
     * Stamina, from 0 to {@link #STAMINA}: running at {@link #RUN} spends it and walking or creeping restores it. An
     * AGGRESSIVE gladiator runs for a long time at a steady pace; an AMBUSH gladiator bursts for a short while and then
     * has to hide until it recovers.
     */
    public static final float STAMINA = 100f;
    /** A lion nearer than this is run from. */
    public static final int FLEE = 300;
    /** How much short of its weapon's reach it swings, as the opponent's position is a tenth of a second old. */
    public static final int STRIKE_MARGIN = 14;
    public static final float STRIKE_AIM = 0.3f;
    public static final int HURT_MILLIS = 1500;
    private static final int STUCK_FRAMES = 20;

    private final int behaviour;
    /** Its class, an {@link Opening} kind: what it fights with ({@link Arms}). */
    private final int kind;
    private final Senses senses = new Senses();
    private final Lair lair = new Lair();
    private final Patrol patrol = new Patrol();
    private int slot;
    private int mode;
    private boolean sees;
    private boolean everSeen;
    private float seenX;
    private float seenY;
    private int seenAt;
    private int hurtAt;
    private float turn;
    private float walk;
    private boolean strike;
    /** Frames in a row that a walk got (almost) nowhere. */
    private int stuck;
    private float stamina = STAMINA;

    public Brain(int behaviour) {
        this(behaviour, GladiatorClass.MURMILLO);
    }

    public Brain(int behaviour, int kind) {
        this.behaviour = behaviour;
        this.kind = kind;
    }

    /** Forgets everything, for a new round from the gate of {@code slot}. */
    public void reset(int gate, int now) {
        slot = gate;
        mode = behaviour == AMBUSH ? HIDING : SEARCHING;
        sees = false;
        everSeen = false;
        hurtAt = now - 10 * HURT_MILLIS;
        stuck = 0;
        stamina = STAMINA;
        patrol.reset(now);
        lair.reset(Coliseum.spawnX(1 - gate), 0f, now);
        senses.reset(kind == GladiatorClass.RETIARIUS, now);
    }

    public int behaviour() {
        return behaviour;
    }

    public int kind() {
        return kind;
    }

    public int slot() {
        return slot;
    }

    public int mode() {
        return mode;
    }

    public void setMode(int doing) {
        mode = doing;
    }

    public Senses senses() {
        return senses;
    }

    public Lair lair() {
        return lair;
    }

    public Patrol patrol() {
        return patrol;
    }

    public boolean sees() {
        return sees;
    }

    public boolean everSeen() {
        return everSeen;
    }

    /** The trail went cold: it no longer chases where it last saw the opponent. */
    public void forget() {
        everSeen = false;
    }

    public float seenX() {
        return seenX;
    }

    public float seenY() {
        return seenY;
    }

    public int seenAt() {
        return seenAt;
    }

    /** This frame's walking pace, before its class's speed. */
    public float walk() {
        return walk;
    }

    /** Whether it swings its weapon this frame. */
    public boolean strike() {
        return strike;
    }

    public float stamina() {
        return stamina;
    }

    /** Runs out of stamina at once, as after a long sprint. */
    public void tire() {
        stamina = 0f;
    }

    /** What the eyes report this frame: the opponent at ({@code x}, {@code y}) when {@code visible}. */
    public void see(boolean visible, float x, float y, int now) {
        sees = visible;
        if (visible) {
            everSeen = true;
            seenX = x;
            seenY = y;
            seenAt = now;
        }
    }

    /** A blow landed on this gladiator. */
    public void hurt(int now) {
        hurtAt = now;
    }

    public boolean recentlyHurt(int now) {
        return now - hurtAt < HURT_MILLIS;
    }

    /** Spins round toward where a blow came from. */
    public void spin() {
        turn = 1.5f * TURN;
    }

    /** Decides this frame's move and makes it. */
    public void drive(Body body, int now) {
        turn = 0f;
        walk = 0f;
        strike = false;
        if (senses.lionOut() && body.distanceTo(senses.lionX(), senses.lionY()) < FLEE) {
            flee(body);
        } else if (behaviour == AMBUSH) {
            Ambush.think(this, body, now);
        } else {
            Aggressive.think(this, body, now);
        }
        body.turn(turn);
        if (walk >= RUN && stamina <= 0f) {
            walk = WALK;
        }
        boolean running = walk >= RUN;
        boolean rooted = senses.rooted();
        if (rooted) {
            walk = 0f;
            strike = false;
        }
        float pace = walk * Arms.speed(kind);
        float moved = pace > 0f ? body.walk(pace) : 0f;
        stuck = pace > 0f && moved < pace * 0.3f ? stuck + 1 : 0;
        spend(running && !rooted);
        senses.aimJavelin(this, body, now);
        senses.aimNet(this, body);
    }

    /** Runs straight away from the lion, round any pillar in the way. */
    private void flee(Body body) {
        mode = FLEEING;
        float away = (float) Math.atan2(body.y() - senses.lionY(), body.x() - senses.lionX());
        goTo(body, body.x() + (float) Math.cos(away) * FLEE, body.y() + (float) Math.sin(away) * FLEE, RUN);
    }

    /** How near the opponent has to be for a swing to reach it. */
    public int strikeRange() {
        return Arms.reach(kind) - STRIKE_MARGIN;
    }

    /** Spends stamina while running and restores it otherwise, at the rate of this gladiator's style. */
    private void spend(boolean running) {
        if (running) {
            stamina = Math.max(0f, stamina - (behaviour == AMBUSH ? 4f : 0.5f));
        } else {
            stamina = Math.min(STAMINA, stamina + (behaviour == AMBUSH ? 1f : 0.5f));
        }
    }

    public boolean isStuck() {
        return stuck > STUCK_FRAMES;
    }

    /** Starts counting stuck frames afresh, for a new way to go. */
    public void unstick() {
        stuck = 0;
    }

    /**
     * Closes in on the opponent in sight, round any pillar between them, and swings when it is in reach, in front and
     * not behind a pillar (the arena counts no blow through one).
     */
    public void fight(Body body) {
        float distance = body.distanceTo(seenX, seenY);
        if (distance > strikeRange() - 6) {
            goTo(body, seenX, seenY, RUN);
        } else {
            face(body, seenX, seenY);
        }
        float off = Math.abs(Eyes.wrap((float) Math.atan2(seenY - body.y(), seenX - body.x()) - body.angle()));
        strike = distance <= strikeRange() && off < STRIKE_AIM
                && body.coliseum().clearSight(body.x(), body.y(), seenX, seenY);
    }

    /** Turns toward the point, at most {@link #TURN}; returns how far off it still faces, in radians. */
    public float face(Body body, float tx, float ty) {
        float off = Eyes.wrap((float) Math.atan2(ty - body.y(), tx - body.x()) - body.angle());
        turn = Math.clamp(off, -TURN, TURN);
        return Math.abs(off - turn);
    }

    /** Heads for the point at {@code speed}, round the side of the first pillar in the way. */
    public void goTo(Body body, float tx, float ty, float speed) {
        Coliseum coliseum = body.coliseum();
        float aimX = tx;
        float aimY = ty;
        int p = coliseum.blocking(body.x(), body.y(), tx, ty, Coliseum.BODY + 4);
        if (p >= 0 && coliseum.pillarAt(tx, ty, Coliseum.BODY + 4) != p) {
            float dx = tx - body.x();
            float dy = ty - body.y();
            float length = Math.max(1f, (float) Math.hypot(dx, dy));
            // The perpendicular to the way, on the side of the pillar it is already nearer to.
            float nx = -dy / length;
            float ny = dx / length;
            float side = (coliseum.pillarX(p) - body.x()) * nx + (coliseum.pillarY(p) - body.y()) * ny;
            float around = coliseum.pillarRadius() + Coliseum.BODY + 20;
            float sign = side > 0f ? -1f : 1f;
            aimX = coliseum.pillarX(p) + nx * around * sign;
            aimY = coliseum.pillarY(p) + ny * around * sign;
        }
        float off = face(body, aimX, aimY);
        walk = off < 1.0f ? speed : speed * 0.3f;
    }
}
