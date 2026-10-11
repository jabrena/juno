package io.github.jabrena.juno.games.gladiator.arena;

import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;

/**
 * The lion the {@link Match} lets loose: {@link #FIRST_MILLIS} into a fight, and every {@link #PERIOD_MILLIS} after,
 * it bounds out of its gate in the north wall and hunts the nearest gladiator for {@link #HUNT_MILLIS}, then goes back.
 * It runs faster than a gladiator walks but slower than one runs, so stamina decides who gets away; a bite the victim
 * did not see coming does double damage, and a lion that kills a gladiator gives the bout to the other one.
 */
public final class Lion {
    public static final int FIRST_MILLIS = 20_000;
    public static final int PERIOD_MILLIS = 40_000;
    public static final int HUNT_MILLIS = 20_000;
    /** Units a millisecond: 5.6 a frame, between a gladiator's walk and its run. */
    public static final float SPEED = 0.14f;
    public static final int DAMAGE = 12;
    public static final int BITE_MILLIS = 1500;
    /** Centre to centre, for a bite. */
    public static final int REACH = 60;
    /** The gate's place along the north wall. */
    public static final float GATE_X = 0f;
    /** How near it has to come back to its gate to be shut in again. */
    private static final int HOME = 40;

    private final Body body;
    private boolean active;
    private int bittenAt;

    public Lion(Coliseum coliseum) {
        body = new Body(coliseum);
    }

    public boolean active() {
        return active;
    }

    public float x() {
        return body.x();
    }

    public float y() {
        return body.y();
    }

    public float angle() {
        return body.angle();
    }

    /** Shut in its cage, for a new round. */
    public void reset() {
        active = false;
    }

    /** Where the gate in the north wall of {@code coliseum} opens. */
    public static float gateY(Coliseum coliseum) {
        return coliseum.radiusY() - 60f;
    }

    /** Whether the lion hunts {@code fightMillis} into a fight, rather than heading home or staying shut in. */
    public static boolean hunting(int fightMillis) {
        int since = fightMillis - FIRST_MILLIS;
        return since >= 0 && since % PERIOD_MILLIS < HUNT_MILLIS;
    }

    /** Lets the lion out at ({@code x}, {@code y}), ready to bite at {@code now}. */
    public void loose(float x, float y, int now) {
        active = true;
        body.place(x, y, 0f);
        bittenAt = now - BITE_MILLIS;
    }

    /** Moves the lion on by {@code millis}: out of its gate, after the nearest gladiator, and home again. */
    public void update(Match match, int millis, int fightMillis, int now) {
        boolean hunt = hunting(fightMillis);
        if (!active) {
            if (!hunt) {
                return;
            }
            loose(GATE_X, gateY(body.coliseum()), now);
        }
        int prey = hunt ? nearest(match) : -1;
        float tx = prey < 0 ? GATE_X : match.side(prey).x();
        float ty = prey < 0 ? gateY(body.coliseum()) : match.side(prey).y();
        float distance = body.distanceTo(tx, ty);
        if (prey < 0 && distance < HOME) {
            active = false;
            return;
        }
        body.face((float) Math.atan2(ty - body.y(), tx - body.x()));
        // It stops a little short of its prey to bite, but walks right into its gate.
        float stop = prey < 0 ? 0f : REACH - 10;
        if (distance > stop) {
            body.walk(Math.min(SPEED * millis, distance - stop));
        }
        if (prey >= 0 && body.distanceTo(tx, ty) <= REACH && now - bittenAt >= BITE_MILLIS) {
            bittenAt = now;
            match.hurt(prey, Arms.CLAWS, body.x(), body.y(), DAMAGE, false, 1 - prey, now);
        }
    }

    private int nearest(Match match) {
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int slot = 0; slot < 2; slot++) {
            Side side = match.side(slot);
            float distance = body.distanceTo(side.x(), side.y());
            if (side.present() && distance < bestDistance) {
                bestDistance = distance;
                best = slot;
            }
        }
        return best;
    }

    /** Where the lion is, into a {@code WORLD} packet; every gladiator may know it. */
    public void write(byte[] packet) {
        packet[GladiatorProtocol.LION] = (byte) (active ? 1 : 0);
        if (active) {
            GladiatorProtocol.writeShort(packet, GladiatorProtocol.LION_X, Math.round(body.x()));
            GladiatorProtocol.writeShort(packet, GladiatorProtocol.LION_Y, Math.round(body.y()));
            GladiatorProtocol.writeShort(packet, GladiatorProtocol.LION_ANGLE, GladiatorProtocol.toWire(body.angle()));
        }
    }
}
