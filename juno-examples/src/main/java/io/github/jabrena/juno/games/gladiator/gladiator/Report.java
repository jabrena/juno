package io.github.jabrena.juno.games.gladiator.gladiator;

import io.github.jabrena.juno.games.gladiator.arena.Coliseum;
import io.github.jabrena.juno.games.gladiator.arena.Javelins;
import io.github.jabrena.juno.games.gladiator.arena.Match;
import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;

/**
 * What a gladiator knows of the battle: the latest {@link GladiatorProtocol#WORLD} the arena sent it. The opponent's
 * position is only there while this gladiator's eyes see it; otherwise {@link #visible()} is false and the last one
 * seen is kept. The smoke it reports goes onto this board's {@link Coliseum}.
 */
public final class Report {
    /** {@link #read} found a new round, a blow that landed on this gladiator, or a swing of the opponent's. */
    public static final int NEW_ROUND = 1;
    public static final int STRUCK = 2;
    public static final int OPPONENT_SWUNG = 4;

    private final Coliseum coliseum;
    private int phase;
    private int round;
    private int winner;
    private int countdown;
    private int health;
    private int wins;
    private boolean surprised;
    private boolean opponentPresent;
    private int opponentKind;
    private int opponentHealth;
    private int opponentWins;
    private boolean visible;
    private float opponentX;
    private float opponentY;
    private float opponentAngle;
    private int flags;
    private int opponentClass;
    private boolean lion;
    private float lionX;
    private float lionY;
    private float lionAngle;
    private final int[] javelin = new int[Javelins.COUNT];
    private final float[] javelinX = new float[Javelins.COUNT];
    private final float[] javelinY = new float[Javelins.COUNT];
    private final float[] javelinAngle = new float[Javelins.COUNT];
    private int hits;
    private int opponentStrikes;

    public Report(Coliseum coliseum) {
        this.coliseum = coliseum;
        clear();
    }

    /** Forgets the last arena's reports, for a gladiator that has just received the map. */
    public void clear() {
        phase = Match.WAITING;
        round = -1;
        hits = -1;
        opponentStrikes = -1;
        opponentPresent = false;
        visible = false;
        coliseum.braziers().setMask(0);
        flags = 0;
        lion = false;
        for (int j = 0; j < Javelins.COUNT; j++) {
            javelin[j] = GladiatorProtocol.JAVELIN_NONE;
        }
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

    public int countdown() {
        return countdown;
    }

    public int health() {
        return health;
    }

    /** How many blows this gladiator has taken, counting up. */
    public int hits() {
        return hits;
    }

    public int wins() {
        return wins;
    }

    public boolean surprised() {
        return surprised;
    }

    public boolean opponentPresent() {
        return opponentPresent;
    }

    /** The opponent's fighting style. */
    public int opponentKind() {
        return opponentKind;
    }

    public int opponentHealth() {
        return opponentHealth;
    }

    public int opponentWins() {
        return opponentWins;
    }

    public boolean visible() {
        return visible;
    }

    public float opponentX() {
        return opponentX;
    }

    public float opponentY() {
        return opponentY;
    }

    public float opponentAngle() {
        return opponentAngle;
    }

    /** Caught in the opponent's net. */
    public boolean rooted() {
        return (flags & GladiatorProtocol.ROOTED) != 0;
    }

    /** Its own net still in hand. */
    public boolean netReady() {
        return (flags & GladiatorProtocol.NET_READY) != 0;
    }

    /** The opponent caught in its net. */
    public boolean opponentRooted() {
        return (flags & GladiatorProtocol.OPPONENT_ROOTED) != 0;
    }

    /** The opponent's class, an {@link Opening} kind. */
    public int opponentClass() {
        return opponentClass;
    }

    /** Whether the lion is out. */
    public boolean lion() {
        return lion;
    }

    public float lionX() {
        return lionX;
    }

    public float lionY() {
        return lionY;
    }

    public float lionAngle() {
        return lionAngle;
    }

    /** What this gladiator knows of javelin {@code j}: {@link #GladiatorProtocol.JAVELIN_GROUND} and the others. */
    public int javelin(int j) {
        return javelin[j];
    }

    public float javelinX(int j) {
        return javelinX[j];
    }

    public float javelinY(int j) {
        return javelinY[j];
    }

    public float javelinAngle(int j) {
        return javelinAngle[j];
    }

    /** Whether this gladiator holds a javelin. */
    public boolean holding() {
        for (int j = 0; j < Javelins.COUNT; j++) {
            if (javelin[j] == GladiatorProtocol.JAVELIN_HELD) {
                return true;
            }
        }
        return false;
    }

    /** Takes in a {@code WORLD} packet; returns what happened, as {@link #NEW_ROUND} and the other flags. */
    public int read(byte[] packet) {
        int events = 0;
        int newRound = GladiatorProtocol.unsigned(packet, GladiatorProtocol.ROUND);
        if (newRound != round) {
            round = newRound;
            opponentStrikes = -1;
            events = NEW_ROUND;
        }
        phase = GladiatorProtocol.unsigned(packet, GladiatorProtocol.PHASE);
        winner = GladiatorProtocol.unsigned(packet, GladiatorProtocol.WINNER);
        countdown = GladiatorProtocol.unsigned(packet, GladiatorProtocol.COUNTDOWN);
        health = GladiatorProtocol.unsigned(packet, GladiatorProtocol.HEALTH);
        wins = GladiatorProtocol.unsigned(packet, GladiatorProtocol.WINS);
        surprised = packet[GladiatorProtocol.SURPRISED] != 0;
        int newHits = GladiatorProtocol.unsigned(packet, GladiatorProtocol.HITS);
        if (hits >= 0 && newHits != hits) {
            events = events | STRUCK;
        }
        hits = newHits;
        opponentPresent = packet[GladiatorProtocol.OPPONENT] != 0;
        opponentKind = GladiatorProtocol.unsigned(packet, GladiatorProtocol.OPPONENT_BEHAVIOUR);
        opponentHealth = GladiatorProtocol.unsigned(packet, GladiatorProtocol.OPPONENT_HEALTH);
        opponentWins = GladiatorProtocol.unsigned(packet, GladiatorProtocol.OPPONENT_WINS);
        coliseum.braziers().setMask(GladiatorProtocol.unsigned(packet, GladiatorProtocol.SMOKE));
        flags = GladiatorProtocol.unsigned(packet, GladiatorProtocol.FLAGS);
        opponentClass = GladiatorProtocol.unsigned(packet, GladiatorProtocol.OPPONENT_CLASS);
        readLion(packet);
        readJavelins(packet);
        visible = packet[GladiatorProtocol.VISIBLE] != 0;
        if (visible) {
            opponentX = GladiatorProtocol.readShort(packet, GladiatorProtocol.OPPONENT_X);
            opponentY = GladiatorProtocol.readShort(packet, GladiatorProtocol.OPPONENT_Y);
            opponentAngle = GladiatorProtocol.fromWire(
                    GladiatorProtocol.readWord(packet, GladiatorProtocol.OPPONENT_ANGLE));
            int swung = GladiatorProtocol.unsigned(packet, GladiatorProtocol.OPPONENT_STRIKES);
            if (opponentStrikes >= 0 && swung != opponentStrikes) {
                events = events | OPPONENT_SWUNG;
            }
            opponentStrikes = swung;
        }
        return events;
    }

    private void readLion(byte[] packet) {
        lion = packet[GladiatorProtocol.LION] != 0;
        lionX = GladiatorProtocol.readShort(packet, GladiatorProtocol.LION_X);
        lionY = GladiatorProtocol.readShort(packet, GladiatorProtocol.LION_Y);
        lionAngle = GladiatorProtocol.fromWire(GladiatorProtocol.readWord(packet, GladiatorProtocol.LION_ANGLE));
    }

    private void readJavelins(byte[] packet) {
        for (int j = 0; j < Javelins.COUNT; j++) {
            javelin[j] = GladiatorProtocol.unsigned(packet, GladiatorProtocol.JAVELIN_STATE + j);
            int at = GladiatorProtocol.JAVELIN_DATA + j * GladiatorProtocol.JAVELIN_STRIDE;
            javelinX[j] = GladiatorProtocol.readShort(packet, at);
            javelinY[j] = GladiatorProtocol.readShort(packet, at + 2);
            javelinAngle[j] = GladiatorProtocol.fromWire(GladiatorProtocol.readWord(packet, at + 4));
        }
    }
}
