package io.github.jabrena.juno.games.gladiator.arena;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;

/**
 * The two javelins the {@link Match} keeps on the sand. A gladiator that walks over one picks it up; thrown, it flies
 * until it strikes the opponent, a pillar or the wall, or runs out of range, and lies where it fell for anyone to pick
 * up again. A javelin the victim did not see coming does double damage, and a scutum or a helmet takes its share
 * ({@link Arms}), like a blade's.
 */
public final class Javelins {
    public static final int COUNT = 2;
    public static final int DAMAGE = 20;
    /** Units a millisecond. */
    public static final float SPEED = 0.9f;
    public static final int RANGE = 1100;
    /** How near a gladiator has to stand to pick a javelin up, and to be struck by one. */
    public static final int PICKUP = 45;
    public static final int GROUND = 0;
    public static final int HELD = 1;
    public static final int FLYING = 2;
    /** Where a javelin lies when no random place fits ({@link #reset}): one a short walk from each gate. */
    public static final short[] START_X = {-1170, 1170};
    public static final short[] START_Y = {300, -300};
    /** A javelin lands in its own half, between these distances from the middle, and this far from a gate. */
    private static final int NEAR_MIDDLE = 270;
    private static final int FAR_SIDE = 1340;
    private static final int FROM_GATE = 300;
    private static final int ATTEMPTS = 200;
    /** Units a javelin moves between checks, short enough that none flies through a body. */
    private static final int STEP = 16;

    private final int[] state = new int[COUNT];
    /** The slot holding, or the slot that threw, each javelin. */
    private final int[] owner = new int[COUNT];
    private final float[] x = new float[COUNT];
    private final float[] y = new float[COUNT];
    private final float[] angle = new float[COUNT];
    private final float[] travel = new float[COUNT];

    public int state(int j) {
        return state[j];
    }

    public int owner(int j) {
        return owner[j];
    }

    public float x(int j) {
        return x[j];
    }

    public float y(int j) {
        return y[j];
    }

    public float angle(int j) {
        return angle[j];
    }

    /** Stands javelin {@code j} in the sand at ({@code px}, {@code py}). */
    public void place(int j, float px, float py) {
        state[j] = GROUND;
        x[j] = px;
        y[j] = py;
    }

    /**
     * Stuck in the sand at a new random place for a round: each javelin in its own half, so each gladiator has one
     * nearer than the other, clear of the pillars and not right at a gate.
     */
    public void reset(Coliseum coliseum) {
        for (int j = 0; j < COUNT; j++) {
            place(j, START_X[j], START_Y[j]);
            angle[j] = Random.nextInt(628) / 100f;
            float side = j == 0 ? -1f : 1f;
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                float px = side * (NEAR_MIDDLE + Random.nextInt(FAR_SIDE - NEAR_MIDDLE));
                float py = Random.nextInt(2 * Coliseum.PLACE_Y + 1) - Coliseum.PLACE_Y;
                if (coliseum.walkable(px, py) && coliseum.pillarAt(px, py, Coliseum.BODY + PICKUP) < 0
                        && Math.hypot(px - Coliseum.spawnX(j), py) >= FROM_GATE) {
                    place(j, px, py);
                    break;
                }
            }
        }
    }

    /** The javelin the gladiator in {@code slot} holds, or -1. */
    public int held(int slot) {
        for (int j = 0; j < COUNT; j++) {
            if (state[j] == HELD && owner[j] == slot) {
                return j;
            }
        }
        return -1;
    }

    /** The gladiator in {@code slot}, standing at ({@code gx}, {@code gy}), picks up a javelin under its feet. */
    public void pickUp(int slot, float gx, float gy) {
        if (held(slot) >= 0) {
            return;
        }
        for (int j = 0; j < COUNT; j++) {
            float dx = x[j] - gx;
            float dy = y[j] - gy;
            if (state[j] == GROUND && dx * dx + dy * dy < PICKUP * PICKUP) {
                state[j] = HELD;
                owner[j] = slot;
                return;
            }
        }
    }

    /** Throws the javelin the gladiator in {@code slot} holds, from just in front of it, the way it faces. */
    public void launch(int slot, float gx, float gy, float facing) {
        int j = held(slot);
        if (j < 0) {
            return;
        }
        state[j] = FLYING;
        angle[j] = facing;
        x[j] = gx + (float) Math.cos(facing) * (Coliseum.BODY + 12);
        y[j] = gy + (float) Math.sin(facing) * (Coliseum.BODY + 12);
        travel[j] = 0f;
    }

    /** Moves every javelin in the air on by {@code millis}; one that strikes lands its blow in the {@code match}. */
    public void fly(Match match, int millis, int now) {
        for (int j = 0; j < COUNT; j++) {
            float remaining = SPEED * millis;
            while (state[j] == FLYING && remaining > 0f) {
                float step = Math.min(STEP, remaining);
                remaining = remaining - step;
                advance(match, j, step, now);
            }
        }
    }

    private void advance(Match match, int j, float step, int now) {
        float nx = x[j] + (float) Math.cos(angle[j]) * step;
        float ny = y[j] + (float) Math.sin(angle[j]) * step;
        int thrower = owner[j];
        Side victim = match.side(1 - thrower);
        float vx = nx - victim.x();
        float vy = ny - victim.y();
        Coliseum coliseum = match.coliseum();
        if (victim.present() && vx * vx + vy * vy < PICKUP * PICKUP) {
            state[j] = GROUND;
            match.wound(thrower, DAMAGE, false, now);
        } else if (!coliseum.inside(nx, ny) || coliseum.pillarAt(nx, ny, 0) >= 0 || travel[j] + step > RANGE) {
            state[j] = GROUND;
        } else {
            x[j] = nx;
            y[j] = ny;
            travel[j] = travel[j] + step;
        }
    }

    /** Each javelin as the gladiator in {@code slot} may know it, into a {@code WORLD} packet. */
    public void write(byte[] packet, int slot) {
        for (int j = 0; j < COUNT; j++) {
            int known = state[j] == GROUND ? GladiatorProtocol.JAVELIN_GROUND
                    : state[j] == FLYING ? GladiatorProtocol.JAVELIN_FLYING
                    : owner[j] == slot ? GladiatorProtocol.JAVELIN_HELD : GladiatorProtocol.JAVELIN_OPPONENT;
            packet[GladiatorProtocol.JAVELIN_STATE + j] = (byte) known;
            if (known == GladiatorProtocol.JAVELIN_GROUND || known == GladiatorProtocol.JAVELIN_FLYING) {
                int at = GladiatorProtocol.JAVELIN_DATA + j * GladiatorProtocol.JAVELIN_STRIDE;
                GladiatorProtocol.writeShort(packet, at, Math.round(x[j]));
                GladiatorProtocol.writeShort(packet, at + 2, Math.round(y[j]));
                GladiatorProtocol.writeShort(packet, at + 4, GladiatorProtocol.toWire(angle[j]));
            }
        }
    }
}
