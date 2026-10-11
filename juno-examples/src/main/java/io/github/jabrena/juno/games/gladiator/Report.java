package io.github.jabrena.juno.games.gladiator;

/**
 * What a gladiator knows of the battle: the latest {@link GladiatorProtocol#WORLD} the arena sent it. The opponent's
 * position is only there while this gladiator's eyes see it; otherwise {@link #visible} is false and the last one
 * seen is kept.
 */
final class Report {
    /** {@link #read} found a new round, a blow that landed on this gladiator, or a swing of the opponent's. */
    static final int NEW_ROUND = 1;
    static final int STRUCK = 2;
    static final int OPPONENT_SWUNG = 4;

    static int phase;
    static int round;
    static int winner;
    static int countdown;
    static int health;
    static int wins;
    static boolean surprised;
    static boolean opponentPresent;
    static int opponentKind;
    static int opponentHealth;
    static int opponentWins;
    static boolean visible;
    static float opponentX;
    static float opponentY;
    static float opponentAngle;
    private static int hits;
    private static int opponentStrikes;

    private Report() {
    }

    /** Forgets the last arena's reports, for a gladiator that has just received the map. */
    static void clear() {
        phase = Match.WAITING;
        round = -1;
        hits = -1;
        opponentStrikes = -1;
        opponentPresent = false;
        visible = false;
    }

    /** Takes in a {@code WORLD} packet; returns what happened, as {@link #NEW_ROUND} and the other flags. */
    static int read(byte[] packet) {
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
}
