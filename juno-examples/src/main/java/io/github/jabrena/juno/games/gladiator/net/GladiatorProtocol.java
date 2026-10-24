package io.github.jabrena.juno.games.gladiator.net;

import io.github.jabrena.juno.games.gladiator.arena.Javelins;

/**
 * The fixed-size UDP packets between the ARENA and the gladiators. Every packet starts with the {@code JUNO} magic,
 * the version, the gladiator service, its type, a slot (0 left, 1 right) and a node id: the sender's in
 * {@link #JOIN} and {@link #STATE}, the recipient's in what the arena answers.
 *
 * <ul>
 *   <li>{@link #JOIN}, broadcast by a gladiator until the arena answers, with its behaviour.</li>
 *   <li>{@link #MAP}, the arena's answer: the slot and the whole {@link Coliseum}. It is sent once, and again only
 *       when a {@code JOIN} shows it was lost.</li>
 *   <li>{@link #FULL}, the arena's answer when both slots are taken.</li>
 *   <li>{@link #STATE}, sent by a gladiator ten times a second: where it stands and faces, and how many strikes it
 *       has swung, a counter, so a lost packet never loses a strike.</li>
 *   <li>{@link #WORLD}, sent by the arena to each gladiator ten times a second: the round, both healths and the
 *       opponent, whose position is filled in only when the recipient's eyes see it.</li>
 * </ul>
 */
public final class GladiatorProtocol {
    public static final int JOIN = 1;
    public static final int MAP = 2;
    public static final int FULL = 3;
    public static final int STATE = 4;
    public static final int WORLD = 5;

    public static final int PACKET_SIZE = 128;
    /** UDP port every board listens on and broadcasts to. */
    public static final int PORT = 4211;
    /** What a gladiator knows of a javelin: nothing yet, on the sand, in its hand, the opponent's, in the air. */
    public static final int JAVELIN_NONE = 0;
    public static final int JAVELIN_GROUND = 1;
    public static final int JAVELIN_HELD = 2;
    public static final int JAVELIN_OPPONENT = 3;
    public static final int JAVELIN_FLYING = 4;
    public static final int TYPE = 6;
    public static final int SLOT = 7;
    private static final int NODE_ID = 8;
    private static final int BODY = 12;

    // JOIN
    public static final int BEHAVIOUR = BODY;
    // MAP
    public static final int RADIUS_X = BODY;
    public static final int RADIUS_Y = BODY + 2;
    public static final int WALL_HEIGHT = BODY + 4;
    public static final int PILLAR_RADIUS = BODY + 6;
    public static final int PILLAR_HEIGHT = BODY + 8;
    public static final int PILLAR_COUNT = BODY + 10;
    public static final int PILLARS = BODY + 11;
    // STATE
    public static final int STATE_ROUND = BODY;
    public static final int STATE_BEHAVIOUR = BODY + 1;
    public static final int X = BODY + 2;
    public static final int Y = BODY + 4;
    public static final int ANGLE = BODY + 6;
    public static final int STRIKES = BODY + 8;
    public static final int MODE = BODY + 9;
    public static final int HUMAN = BODY + 10;
    /** Javelins thrown so far, counted like {@link #STRIKES}. */
    public static final int THROWS = BODY + 11;
    /** The gladiator's class, an {@link Opening} kind, and the nets it cast so far. */
    public static final int STATE_KIND = BODY + 12;
    public static final int NETS = BODY + 13;
    // WORLD
    public static final int PHASE = BODY;
    public static final int ROUND = BODY + 1;
    public static final int WINNER = BODY + 2;
    public static final int HEALTH = BODY + 3;
    public static final int WINS = BODY + 4;
    public static final int HITS = BODY + 5;
    public static final int SURPRISED = BODY + 6;
    public static final int OPPONENT = BODY + 7;
    public static final int OPPONENT_BEHAVIOUR = BODY + 8;
    public static final int OPPONENT_HEALTH = BODY + 9;
    public static final int OPPONENT_WINS = BODY + 10;
    public static final int VISIBLE = BODY + 11;
    public static final int OPPONENT_X = BODY + 12;
    public static final int OPPONENT_Y = BODY + 14;
    public static final int OPPONENT_ANGLE = BODY + 16;
    public static final int OPPONENT_STRIKES = BODY + 18;
    public static final int COUNTDOWN = BODY + 19;
    /** Which {@link Braziers} are smoking, one bit each. */
    public static final int SMOKE = BODY + 20;
    /** Each javelin's state as this gladiator may know it ({@link Report#JAVELIN_GROUND} and the others). */
    public static final int JAVELIN_STATE = BODY + 21;
    /** Each javelin's x, y and heading, {@link #JAVELIN_STRIDE} bytes apart; zero while the opponent holds it. */
    public static final int JAVELIN_DATA = BODY + 21 + Javelins.COUNT;
    public static final int JAVELIN_STRIDE = 6;
    /** {@link #ROOTED}, {@link #NET_READY} and {@link #OPPONENT_ROOTED}. */
    public static final int FLAGS = JAVELIN_DATA + Javelins.COUNT * JAVELIN_STRIDE;
    public static final int ROOTED = 1;
    public static final int NET_READY = 2;
    public static final int OPPONENT_ROOTED = 4;
    /** The opponent's class, an {@link Opening} kind. */
    public static final int OPPONENT_CLASS = FLAGS + 1;
    /** Whether the {@link Lion} is out, and where it stands and faces. */
    public static final int LION = OPPONENT_CLASS + 1;
    public static final int LION_X = LION + 1;
    public static final int LION_Y = LION + 3;
    public static final int LION_ANGLE = LION + 5;

    private static final int VERSION = 1;
    private static final int GLADIATOR_SERVICE = 4;

    private GladiatorProtocol() {
    }

    /** Writes the header and clears the rest of the packet. */
    public static void begin(byte[] packet, int type, int slot, int nodeId) {
        for (int i = 0; i < PACKET_SIZE; i++) {
            packet[i] = 0;
        }
        packet[0] = 'J';
        packet[1] = 'U';
        packet[2] = 'N';
        packet[3] = 'O';
        packet[4] = VERSION;
        packet[5] = GLADIATOR_SERVICE;
        packet[TYPE] = (byte) type;
        packet[SLOT] = (byte) slot;
        writeInt(packet, NODE_ID, nodeId);
    }

    public static boolean valid(byte[] packet, int length) {
        return length == PACKET_SIZE
                && packet[0] == 'J' && packet[1] == 'U' && packet[2] == 'N' && packet[3] == 'O'
                && unsigned(packet, 4) == VERSION && unsigned(packet, 5) == GLADIATOR_SERVICE;
    }

    public static int type(byte[] packet) {
        return unsigned(packet, TYPE);
    }

    public static int slot(byte[] packet) {
        return unsigned(packet, SLOT);
    }

    public static int nodeId(byte[] packet) {
        return unsigned(packet, NODE_ID) << 24 | unsigned(packet, NODE_ID + 1) << 16
                | unsigned(packet, NODE_ID + 2) << 8 | unsigned(packet, NODE_ID + 3);
    }

    /** An angle in radians as one of a turn's 65536 steps, the way it travels. */
    public static int toWire(float angle) {
        return Math.round(angle * (float) (32768 / Math.PI)) & 0xFFFF;
    }

    public static float fromWire(int angle) {
        return angle * (float) (Math.PI / 32768);
    }

    public static int unsigned(byte[] packet, int offset) {
        return packet[offset] & 0xff;
    }

    public static void writeShort(byte[] packet, int offset, int value) {
        packet[offset] = (byte) (value >>> 8);
        packet[offset + 1] = (byte) value;
    }

    /** A signed 16-bit value. */
    public static int readShort(byte[] packet, int offset) {
        return (short) (unsigned(packet, offset) << 8 | unsigned(packet, offset + 1));
    }

    /** An unsigned 16-bit value. */
    public static int readWord(byte[] packet, int offset) {
        return unsigned(packet, offset) << 8 | unsigned(packet, offset + 1);
    }

    private static void writeInt(byte[] packet, int offset, int value) {
        packet[offset] = (byte) (value >>> 24);
        packet[offset + 1] = (byte) (value >>> 16);
        packet[offset + 2] = (byte) (value >>> 8);
        packet[offset + 3] = (byte) value;
    }
}
