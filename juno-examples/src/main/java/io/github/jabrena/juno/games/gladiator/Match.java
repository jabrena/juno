package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.net.Udp;

/**
 * The state the ARENA keeps: who holds the left and right slots, where each gladiator stands, both healths, the
 * round and its phase. Gladiators report their own moves; the arena alone decides what a strike does, so two boards
 * never disagree about a hit.
 *
 * <p>A round waits for two gladiators ({@link #WAITING}), counts down ({@link #COUNTDOWN}), is fought
 * ({@link #FIGHT}) until one falls, and shows the victor ({@link #OVER}) before both start again from their gates.
 * A strike hits when the opponent is within {@link #REACH} in front of the striker with no pillar between them; one
 * the victim's eyes did not see coming does double damage. A gladiator unheard for {@link #TIMEOUT_MILLIS} leaves
 * its slot to the next one that joins.
 */
final class Match {
    static final int WAITING = 0;
    static final int COUNTDOWN = 1;
    static final int FIGHT = 2;
    static final int OVER = 3;
    static final int NOBODY = 255;

    static final int HEALTH = 100;
    static final int DAMAGE = 10;
    /** Centre to centre: two bodies and a gladius. */
    static final int REACH = 80;
    /** How far either side of where the striker faces the blade sweeps. */
    static final float STRIKE_ARC = 0.6f;
    static final int STRIKE_MILLIS = 600;
    static final int TIMEOUT_MILLIS = 3000;
    static final int COUNTDOWN_MILLIS = 3000;
    static final int OVER_MILLIS = 5000;

    final int[] node = new int[2];
    final boolean[] present = new boolean[2];
    final int[] behaviour = new int[2];
    final int[] x = new int[2];
    final int[] y = new int[2];
    /** Facing, in the wire's 65536 steps per turn. */
    final int[] angle = new int[2];
    final int[] health = new int[2];
    final int[] wins = new int[2];
    final int[] mode = new int[2];
    final boolean[] human = new boolean[2];
    final int[] strikes = new int[2];
    final int[] hits = new int[2];
    final boolean[] surprised = new boolean[2];
    private final int[] heard = new int[2];
    private final int[] struckAt = new int[2];
    private final int[] left = new int[Udp.ENDPOINT_SIZE];
    private final int[] right = new int[Udp.ENDPOINT_SIZE];

    int phase = WAITING;
    int round;
    int winner = NOBODY;
    private int phaseAt;

    /** Counts the strikes that landed; with {@link #lastAttacker} and {@link #lastDamage} for the log. */
    int landed;
    int lastAttacker;
    int lastDamage;

    /**
     * Gives the gladiator {@code nodeId} a slot, or its own again when it was already in one, and returns it; -1 when
     * the arena is full. {@code source} is where its packets come from, kept to answer it.
     */
    int join(int nodeId, int kind, int[] source, int now) {
        int slot = slotOf(nodeId);
        if (slot < 0) {
            slot = !present[0] ? 0 : !present[1] ? 1 : -1;
            if (slot < 0) {
                return -1;
            }
            present[slot] = true;
            node[slot] = nodeId;
            wins[slot] = 0;
            mode[slot] = 0;
            human[slot] = false;
            strikes[slot] = 0;
            place(slot);
            if (present[1 - slot]) {
                startRound(now);
            }
        }
        behaviour[slot] = kind;
        heard[slot] = now;
        int[] endpoint = endpoint(slot);
        for (int i = 0; i < Udp.ENDPOINT_SIZE; i++) {
            endpoint[i] = source[i];
        }
        return slot;
    }

    /** The slot of the gladiator {@code nodeId}, or -1. */
    int slotOf(int nodeId) {
        for (int slot = 0; slot < 2; slot++) {
            if (present[slot] && node[slot] == nodeId) {
                return slot;
            }
        }
        return -1;
    }

    int[] endpoint(int slot) {
        return slot == 0 ? left : right;
    }

    /** Takes a {@link GladiatorProtocol#STATE} packet from the gladiator in {@code slot}. */
    void state(int slot, byte[] packet, int now) {
        heard[slot] = now;
        behaviour[slot] = GladiatorProtocol.unsigned(packet, GladiatorProtocol.STATE_BEHAVIOUR);
        mode[slot] = GladiatorProtocol.unsigned(packet, GladiatorProtocol.MODE);
        human[slot] = packet[GladiatorProtocol.HUMAN] != 0;
        if (GladiatorProtocol.unsigned(packet, GladiatorProtocol.STATE_ROUND) != (round & 0xFF)) {
            // Still standing where the last round ended; it moves back to its gate when it hears the new one.
            return;
        }
        int newX = GladiatorProtocol.readShort(packet, GladiatorProtocol.X);
        int newY = GladiatorProtocol.readShort(packet, GladiatorProtocol.Y);
        if (phase == FIGHT && Coliseum.walkable(newX, newY)) {
            x[slot] = newX;
            y[slot] = newY;
            angle[slot] = GladiatorProtocol.readWord(packet, GladiatorProtocol.ANGLE);
        }
        int swung = GladiatorProtocol.unsigned(packet, GladiatorProtocol.STRIKES);
        if (swung != strikes[slot]) {
            strikes[slot] = swung;
            if (phase == FIGHT && now - struckAt[slot] >= STRIKE_MILLIS) {
                struckAt[slot] = now;
                strike(slot, now);
            }
        }
    }

    /** Moves the rounds on and lets go of gladiators that went silent. */
    void tick(int now) {
        for (int slot = 0; slot < 2; slot++) {
            if (present[slot] && now - heard[slot] > TIMEOUT_MILLIS) {
                present[slot] = false;
                phase = WAITING;
                winner = NOBODY;
                round = round + 1;
            }
        }
        if (phase == COUNTDOWN && now - phaseAt >= COUNTDOWN_MILLIS) {
            phase = FIGHT;
            phaseAt = now;
        } else if (phase == OVER && now - phaseAt >= OVER_MILLIS) {
            startRound(now);
        }
    }

    /** Seconds left on the countdown, for the screens. */
    int countdown(int now) {
        return phase == COUNTDOWN ? (COUNTDOWN_MILLIS - (now - phaseAt) + 999) / 1000 : 0;
    }

    /** Whether the gladiator in {@code slot} sees its opponent. */
    boolean sees(int slot) {
        int other = 1 - slot;
        return present[slot] && present[other] && phase != WAITING
                && Eyes.sees(x[slot], y[slot], GladiatorProtocol.fromWire(angle[slot]), x[other], y[other]);
    }

    /** The {@link GladiatorProtocol#WORLD} packet for the gladiator in {@code slot}: what it may know. */
    void writeWorld(byte[] packet, int slot, int now) {
        int other = 1 - slot;
        GladiatorProtocol.begin(packet, GladiatorProtocol.WORLD, slot, node[slot]);
        packet[GladiatorProtocol.PHASE] = (byte) phase;
        packet[GladiatorProtocol.ROUND] = (byte) round;
        packet[GladiatorProtocol.WINNER] = (byte) winner;
        packet[GladiatorProtocol.HEALTH] = (byte) health[slot];
        packet[GladiatorProtocol.WINS] = (byte) wins[slot];
        packet[GladiatorProtocol.HITS] = (byte) hits[slot];
        packet[GladiatorProtocol.SURPRISED] = (byte) (surprised[slot] ? 1 : 0);
        packet[GladiatorProtocol.COUNTDOWN] = (byte) countdown(now);
        if (!present[other]) {
            return;
        }
        packet[GladiatorProtocol.OPPONENT] = 1;
        packet[GladiatorProtocol.OPPONENT_BEHAVIOUR] = (byte) behaviour[other];
        packet[GladiatorProtocol.OPPONENT_HEALTH] = (byte) health[other];
        packet[GladiatorProtocol.OPPONENT_WINS] = (byte) wins[other];
        if (sees(slot)) {
            packet[GladiatorProtocol.VISIBLE] = 1;
            GladiatorProtocol.writeShort(packet, GladiatorProtocol.OPPONENT_X, x[other]);
            GladiatorProtocol.writeShort(packet, GladiatorProtocol.OPPONENT_Y, y[other]);
            GladiatorProtocol.writeShort(packet, GladiatorProtocol.OPPONENT_ANGLE, angle[other]);
            packet[GladiatorProtocol.OPPONENT_STRIKES] = (byte) strikes[other];
        }
    }

    private void strike(int attacker, int now) {
        int victim = 1 - attacker;
        if (!present[victim]) {
            return;
        }
        float dx = x[victim] - x[attacker];
        float dy = y[victim] - y[attacker];
        if (dx * dx + dy * dy > REACH * REACH) {
            return;
        }
        float facing = GladiatorProtocol.fromWire(angle[attacker]);
        float off = Eyes.wrap((float) Math.atan2(dy, dx) - facing);
        if (Math.abs(off) > STRIKE_ARC || !Coliseum.clearSight(x[attacker], y[attacker], x[victim], y[victim])) {
            return;
        }
        boolean unseen = !Eyes.sees(x[victim], y[victim], GladiatorProtocol.fromWire(angle[victim]),
                x[attacker], y[attacker]);
        int damage = unseen ? 2 * DAMAGE : DAMAGE;
        health[victim] = Math.max(0, health[victim] - damage);
        hits[victim] = (hits[victim] + 1) & 0xFF;
        surprised[victim] = unseen;
        landed = landed + 1;
        lastAttacker = attacker;
        lastDamage = damage;
        if (health[victim] == 0) {
            wins[attacker] = wins[attacker] + 1;
            winner = attacker;
            phase = OVER;
            phaseAt = now;
        }
    }

    private void startRound(int now) {
        round = round + 1;
        phase = COUNTDOWN;
        phaseAt = now;
        winner = NOBODY;
        place(0);
        place(1);
    }

    /** Puts the gladiator in {@code slot} at its gate with full health. */
    private void place(int slot) {
        x[slot] = Coliseum.spawnX(slot);
        y[slot] = 0;
        angle[slot] = GladiatorProtocol.toWire(Coliseum.spawnAngle(slot));
        health[slot] = HEALTH;
        surprised[slot] = false;
    }
}
