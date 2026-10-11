package io.github.jabrena.juno.games.gladiator.arena;

import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;

/**
 * One gate's gladiator as the {@link Match} knows it: which board holds the slot, where it stands and faces, its
 * health and wins, what it is doing, its class, and the counters its {@code STATE} packets carry, so a lost packet
 * never loses a strike, a throw or a net.
 */
public final class Side {
    private final int[] endpoint = new int[Udp.ENDPOINT_SIZE];
    private int node;
    private boolean present;
    private int behaviour;
    private int x;
    private int y;
    /** Facing, in the wire's 65536 steps per turn. */
    private int angle;
    private int health;
    private int wins;
    private int mode;
    private boolean human;
    private int kind;
    private int strikes;
    private int hits;
    private boolean surprised;
    private int heard;
    private int struckAt;
    private int throwsSeen;
    private int netsSeen;
    private boolean netUsed;
    private int rootedUntil;

    public int node() {
        return node;
    }

    public boolean present() {
        return present;
    }

    public int behaviour() {
        return behaviour;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    /** Facing, in the wire's steps. */
    public int angle() {
        return angle;
    }

    /** Facing, in radians. */
    public float facing() {
        return GladiatorProtocol.fromWire(angle);
    }

    public int health() {
        return health;
    }

    public int wins() {
        return wins;
    }

    public int mode() {
        return mode;
    }

    public boolean human() {
        return human;
    }

    public int kind() {
        return kind;
    }

    public int strikes() {
        return strikes;
    }

    public int hits() {
        return hits;
    }

    public boolean surprised() {
        return surprised;
    }

    public int heard() {
        return heard;
    }

    /** Where this gladiator's packets come from, kept to answer it. */
    public int[] endpoint() {
        return endpoint;
    }

    /** The board {@code nodeId} takes the slot, with its counters from zero. */
    public void enter(int nodeId) {
        present = true;
        node = nodeId;
        wins = 0;
        mode = 0;
        human = false;
        strikes = 0;
        throwsSeen = 0;
        netsSeen = 0;
        kind = GladiatorClass.MURMILLO;
    }

    public void leave() {
        present = false;
    }

    /** Heard from at {@code now}, from {@code source}, fighting in {@code style}. */
    public void hear(int style, int[] source, int now) {
        behaviour = style;
        heard = now;
        for (int i = 0; i < Udp.ENDPOINT_SIZE; i++) {
            endpoint[i] = source[i];
        }
    }

    /** What its latest {@code STATE} says it is: style, what it is doing, who drives it and its class. */
    public void report(int style, int doing, boolean byHuman, int asKind, int now) {
        behaviour = style;
        mode = doing;
        human = byHuman;
        kind = asKind;
        heard = now;
    }

    /** At its gate for a new round: full health, net in hand, free. */
    public void startAt(int gateX, int gateY, int facing, int now) {
        x = gateX;
        y = gateY;
        angle = facing;
        health = Match.HEALTH;
        surprised = false;
        netUsed = false;
        rootedUntil = now;
    }

    public void moveTo(int newX, int newY) {
        x = newX;
        y = newY;
    }

    public void face(int facing) {
        angle = facing;
    }

    /** Takes the strike counter of a {@code STATE}; whether it counts a new swing. */
    public boolean newStrikes(int swung) {
        boolean changed = swung != strikes;
        strikes = swung;
        return changed;
    }

    /** Takes the throw counter of a {@code STATE}; whether it counts a new throw. */
    public boolean newThrows(int thrown) {
        boolean changed = thrown != throwsSeen;
        throwsSeen = thrown;
        return changed;
    }

    /** Takes the net counter of a {@code STATE}; whether it counts a new cast. */
    public boolean newNets(int cast) {
        boolean changed = cast != netsSeen;
        netsSeen = cast;
        return changed;
    }

    /** Whether its weapon is ready to strike again at {@code now}, once {@code millis} after the last. */
    public boolean readyToStrike(int now, int millis) {
        return now - struckAt >= millis;
    }

    public void struck(int now) {
        struckAt = now;
    }

    /** Whether it is a RETIARIUS that has not cast its net this round. */
    public boolean netReady() {
        return kind == GladiatorClass.RETIARIUS && !netUsed;
    }

    public void useNet() {
        netUsed = true;
    }

    /** Caught in a net until {@code until}. */
    public void root(int until) {
        rootedUntil = until;
    }

    public boolean rooted(int now) {
        return now - rootedUntil < 0;
    }

    /** A blow of {@code damage} lands; {@code unseen} when it never saw it coming. */
    public void takeBlow(int damage, boolean unseen) {
        health = Math.max(0, health - damage);
        hits = (hits + 1) & 0xFF;
        surprised = unseen;
    }

    public void win() {
        wins = wins + 1;
    }
}
