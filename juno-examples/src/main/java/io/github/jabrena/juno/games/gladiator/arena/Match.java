package io.github.jabrena.juno.games.gladiator.arena;

import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;

/**
 * The state the ARENA keeps: who holds the left and right slots ({@link Side}), where each gladiator stands, both
 * healths, the round and its phase. Gladiators report their own moves; the arena alone decides what a strike does, so
 * two boards never disagree about a hit.
 *
 * <p>A round waits for two gladiators ({@link #WAITING}), counts down ({@link #COUNTDOWN}), is fought
 * ({@link #FIGHT}) until one falls, and shows the victor ({@link #OVER}) before both start again from their gates.
 * A strike hits when the opponent is within its weapon's reach in front of the striker with no pillar between them;
 * one the victim's eyes did not see coming does double damage. A gladiator unheard for {@link #TIMEOUT_MILLIS}
 * leaves its slot to the next one that joins.
 *
 * <p>Two {@link Javelins} lie on the sand, the {@link Braziers} smoke and the {@link Lion} hunts on the fight's
 * clock, all on its {@link Coliseum}.
 */
public final class Match {
    public static final int WAITING = 0;
    public static final int COUNTDOWN = 1;
    public static final int FIGHT = 2;
    public static final int OVER = 3;
    public static final int NOBODY = 255;

    public static final int HEALTH = 100;
    public static final int DAMAGE = 10;
    /** Centre to centre: two bodies and a gladius. */
    public static final int REACH = 80;
    /** How far either side of where the striker faces the blade sweeps. */
    public static final float STRIKE_ARC = 0.6f;
    public static final int TIMEOUT_MILLIS = 3000;
    public static final int COUNTDOWN_MILLIS = 3000;
    public static final int OVER_MILLIS = 5000;

    private final Coliseum coliseum;
    private final Side left = new Side();
    private final Side right = new Side();
    private final Javelins javelins = new Javelins();
    private final Lion lion;
    private int lastTick;
    private int phase = WAITING;
    private int round;
    private int winner = NOBODY;
    private int phaseAt;
    /** Counts the blows that landed; with the last one's attacker and damage for the log. */
    private int landed;
    private int lastAttacker;
    private int lastDamage;
    private boolean lastByLion;

    public Match(Coliseum coliseum) {
        this.coliseum = coliseum;
        lion = new Lion(coliseum);
    }

    public Coliseum coliseum() {
        return coliseum;
    }

    /** The gladiator in {@code slot}: 0 the left gate, 1 the right. */
    public Side side(int slot) {
        return slot == 0 ? left : right;
    }

    public Javelins javelins() {
        return javelins;
    }

    public Lion lion() {
        return lion;
    }

    public int phase() {
        return phase;
    }

    public int round() {
        return round;
    }

    public int winner() {
        return winner;
    }

    public int landed() {
        return landed;
    }

    public int lastAttacker() {
        return lastAttacker;
    }

    public int lastDamage() {
        return lastDamage;
    }

    /** Whether the last blow was the lion's, rather than {@link #lastAttacker()}'s. */
    public boolean lastByLion() {
        return lastByLion;
    }

    /**
     * Gives the gladiator {@code nodeId} a slot, or its own again when it was already in one, and returns it; -1 when
     * the arena is full. {@code source} is where its packets come from, kept to answer it.
     */
    public int join(int nodeId, int style, int[] source, int now) {
        int slot = slotOf(nodeId);
        if (slot < 0) {
            slot = !left.present() ? 0 : !right.present() ? 1 : -1;
            if (slot < 0) {
                return -1;
            }
            side(slot).enter(nodeId);
            place(slot, now);
            if (side(1 - slot).present()) {
                startRound(now);
            }
        }
        side(slot).hear(style, source, now);
        return slot;
    }

    /** The slot of the gladiator {@code nodeId}, or -1. */
    public int slotOf(int nodeId) {
        for (int slot = 0; slot < 2; slot++) {
            if (side(slot).present() && side(slot).node() == nodeId) {
                return slot;
            }
        }
        return -1;
    }

    /** Takes a {@link GladiatorProtocol#STATE} packet from the gladiator in {@code slot}. */
    public void state(int slot, byte[] packet, int now) {
        Side side = side(slot);
        side.report(GladiatorProtocol.unsigned(packet, GladiatorProtocol.STATE_BEHAVIOUR),
                GladiatorProtocol.unsigned(packet, GladiatorProtocol.MODE), packet[GladiatorProtocol.HUMAN] != 0,
                Math.clamp(GladiatorProtocol.unsigned(packet, GladiatorProtocol.STATE_KIND), GladiatorClass.MURMILLO,
                        GladiatorClass.SECUTOR), now);
        if (GladiatorProtocol.unsigned(packet, GladiatorProtocol.STATE_ROUND) != (round & 0xFF)) {
            // Still standing where the last round ended; it moves back to its gate when it hears the new one.
            return;
        }
        int newX = GladiatorProtocol.readShort(packet, GladiatorProtocol.X);
        int newY = GladiatorProtocol.readShort(packet, GladiatorProtocol.Y);
        if (phase == FIGHT && coliseum.walkable(newX, newY)) {
            // A netted gladiator can still turn, but neither walk away nor strike.
            if (!side.rooted(now)) {
                side.moveTo(newX, newY);
            }
            side.face(GladiatorProtocol.readWord(packet, GladiatorProtocol.ANGLE));
        }
        if (side.newStrikes(GladiatorProtocol.unsigned(packet, GladiatorProtocol.STRIKES)) && phase == FIGHT
                && !side.rooted(now) && side.readyToStrike(now, Arms.swingMillis(side.kind()) - 100)) {
            side.struck(now);
            Combat.strike(this, slot, now);
        }
        if (side.newThrows(GladiatorProtocol.unsigned(packet, GladiatorProtocol.THROWS)) && phase == FIGHT) {
            javelins.launch(slot, side.x(), side.y(), side.facing());
        }
        if (side.newNets(GladiatorProtocol.unsigned(packet, GladiatorProtocol.NETS)) && phase == FIGHT) {
            Combat.castNet(this, slot, now);
        }
        if (phase == FIGHT) {
            javelins.pickUp(slot, side.x(), side.y());
        }
    }

    /** Whether the gladiator in {@code slot}, with its class's field of view, sees the point ({@code tx}, {@code ty}). */
    public boolean watches(int slot, float tx, float ty) {
        Side side = side(slot);
        return Eyes.sees(coliseum, side.x(), side.y(), side.facing(), tx, ty, Arms.halfView(side.kind()));
    }

    /** Moves the rounds on and lets go of gladiators that went silent. */
    public void tick(int now) {
        for (int slot = 0; slot < 2; slot++) {
            if (side(slot).present() && now - side(slot).heard() > TIMEOUT_MILLIS) {
                side(slot).leave();
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
        int millis = Math.clamp(now - lastTick, 0, 100);
        lastTick = now;
        Braziers braziers = coliseum.braziers();
        if (phase == FIGHT) {
            javelins.fly(this, millis, now);
            lion.update(this, millis, now - phaseAt, now);
            braziers.setMask(Braziers.ENABLED ? Braziers.maskAt(now - phaseAt) : 0);
        } else {
            braziers.setMask(0);
        }
    }

    /** Seconds left on the countdown, for the screens. */
    public int countdown(int now) {
        return phase == COUNTDOWN ? (COUNTDOWN_MILLIS - (now - phaseAt) + 999) / 1000 : 0;
    }

    /** Whether the gladiator in {@code slot} sees its opponent. */
    public boolean sees(int slot) {
        Side other = side(1 - slot);
        return side(slot).present() && other.present() && phase != WAITING && watches(slot, other.x(), other.y());
    }

    /** The {@link GladiatorProtocol#WORLD} packet for the gladiator in {@code slot}: what it may know. */
    public void writeWorld(byte[] packet, int slot, int now) {
        WorldPacket.write(this, packet, slot, now);
    }

    /**
     * A blow of {@code base} by {@code attacker}, with a {@code blade} or a javelin: doubled when the victim did not
     * see it coming, then changed by both gladiators' {@link Arms}.
     */
    public void wound(int attacker, int base, boolean blade, int now) {
        Side striker = side(attacker);
        hurt(1 - attacker, striker.kind(), striker.x(), striker.y(), base, blade, attacker, now);
    }

    /**
     * A blow of {@code base} on the gladiator in {@code victim}, from ({@code fromX}, {@code fromY}) with
     * {@code arms} ({@link Arms#CLAWS} for the lion); {@code credited} wins the bout when it is the last.
     */
    public void hurt(int victim, int arms, float fromX, float fromY, int base, boolean blade, int credited, int now) {
        Side side = side(victim);
        boolean unseen = !watches(victim, fromX, fromY);
        float off = Eyes.wrap((float) Math.atan2(fromY - side.y(), fromX - side.x()) - side.facing());
        lastByLion = arms == Arms.CLAWS;
        land(credited, Arms.blow(unseen ? 2 * base : base, arms, blade, side.kind(), off), unseen, now);
    }

    /** A blow of {@code damage} by {@code attacker} lands; the bout ends when it was the last. */
    private void land(int attacker, int damage, boolean unseen, int now) {
        Side victim = side(1 - attacker);
        victim.takeBlow(damage, unseen);
        landed = landed + 1;
        lastAttacker = attacker;
        lastDamage = damage;
        if (victim.health() == 0) {
            side(attacker).win();
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
        place(0, now);
        place(1, now);
        javelins.reset(coliseum);
        lion.reset();
    }

    /** Puts the gladiator in {@code slot} at its gate with full health. */
    private void place(int slot, int now) {
        side(slot).startAt(Coliseum.spawnX(slot), 0, GladiatorProtocol.toWire(Coliseum.spawnAngle(slot)), now);
    }
}
