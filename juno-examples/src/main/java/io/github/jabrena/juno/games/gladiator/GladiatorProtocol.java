package io.github.jabrena.juno.games.gladiator;

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
final class GladiatorProtocol {
    static final int JOIN = 1;
    static final int MAP = 2;
    static final int FULL = 3;
    static final int STATE = 4;
    static final int WORLD = 5;

    static final int PACKET_SIZE = 96;
    static final int TYPE = 6;
    static final int SLOT = 7;
    private static final int NODE_ID = 8;
    private static final int BODY = 12;

    // JOIN
    static final int BEHAVIOUR = BODY;
    // MAP
    private static final int RADIUS_X = BODY;
    private static final int RADIUS_Y = BODY + 2;
    private static final int WALL_HEIGHT = BODY + 4;
    private static final int PILLAR_RADIUS = BODY + 6;
    private static final int PILLAR_HEIGHT = BODY + 8;
    private static final int PILLAR_COUNT = BODY + 10;
    private static final int PILLARS = BODY + 11;
    // STATE
    static final int STATE_ROUND = BODY;
    static final int STATE_BEHAVIOUR = BODY + 1;
    static final int X = BODY + 2;
    static final int Y = BODY + 4;
    static final int ANGLE = BODY + 6;
    static final int STRIKES = BODY + 8;
    static final int MODE = BODY + 9;
    static final int HUMAN = BODY + 10;
    // WORLD
    static final int PHASE = BODY;
    static final int ROUND = BODY + 1;
    static final int WINNER = BODY + 2;
    static final int HEALTH = BODY + 3;
    static final int WINS = BODY + 4;
    static final int HITS = BODY + 5;
    static final int SURPRISED = BODY + 6;
    static final int OPPONENT = BODY + 7;
    static final int OPPONENT_BEHAVIOUR = BODY + 8;
    static final int OPPONENT_HEALTH = BODY + 9;
    static final int OPPONENT_WINS = BODY + 10;
    static final int VISIBLE = BODY + 11;
    static final int OPPONENT_X = BODY + 12;
    static final int OPPONENT_Y = BODY + 14;
    static final int OPPONENT_ANGLE = BODY + 16;
    static final int OPPONENT_STRIKES = BODY + 18;
    static final int COUNTDOWN = BODY + 19;

    private static final int VERSION = 1;
    private static final int GLADIATOR_SERVICE = 4;

    private GladiatorProtocol() {
    }

    /** Writes the header and clears the rest of the packet. */
    static void begin(byte[] packet, int type, int slot, int nodeId) {
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

    static boolean valid(byte[] packet, int length) {
        return length == PACKET_SIZE
                && packet[0] == 'J' && packet[1] == 'U' && packet[2] == 'N' && packet[3] == 'O'
                && unsigned(packet, 4) == VERSION && unsigned(packet, 5) == GLADIATOR_SERVICE;
    }

    static int type(byte[] packet) {
        return unsigned(packet, TYPE);
    }

    static int slot(byte[] packet) {
        return unsigned(packet, SLOT);
    }

    static int nodeId(byte[] packet) {
        return unsigned(packet, NODE_ID) << 24 | unsigned(packet, NODE_ID + 1) << 16
                | unsigned(packet, NODE_ID + 2) << 8 | unsigned(packet, NODE_ID + 3);
    }

    /** The map packet: the {@link Coliseum} this board built, for the gladiator in {@code slot}. */
    static void writeMap(byte[] packet, int slot, int nodeId) {
        begin(packet, MAP, slot, nodeId);
        writeShort(packet, RADIUS_X, Coliseum.radiusX);
        writeShort(packet, RADIUS_Y, Coliseum.radiusY);
        writeShort(packet, WALL_HEIGHT, Coliseum.wallHeight);
        writeShort(packet, PILLAR_RADIUS, Coliseum.pillarRadius);
        writeShort(packet, PILLAR_HEIGHT, Coliseum.pillarHeight);
        packet[PILLAR_COUNT] = (byte) Coliseum.pillars;
        for (int p = 0; p < Coliseum.pillars; p++) {
            writeShort(packet, PILLARS + 4 * p, Coliseum.pillarX[p]);
            writeShort(packet, PILLARS + 4 * p + 2, Coliseum.pillarY[p]);
        }
    }

    /** Loads the {@link Coliseum} from a map packet; false, leaving it unchanged, when the packet is malformed. */
    static boolean readMap(byte[] packet) {
        int count = unsigned(packet, PILLAR_COUNT);
        int radiusX = readShort(packet, RADIUS_X);
        int radiusY = readShort(packet, RADIUS_Y);
        if (count > Coliseum.MAX_PILLARS || radiusX <= Coliseum.BODY * 2 || radiusY <= Coliseum.BODY * 2) {
            return false;
        }
        Coliseum.radiusX = radiusX;
        Coliseum.radiusY = radiusY;
        Coliseum.wallHeight = readShort(packet, WALL_HEIGHT);
        Coliseum.pillarRadius = readShort(packet, PILLAR_RADIUS);
        Coliseum.pillarHeight = readShort(packet, PILLAR_HEIGHT);
        Coliseum.pillars = 0;
        for (int p = 0; p < count; p++) {
            Coliseum.addPillar(readShort(packet, PILLARS + 4 * p), readShort(packet, PILLARS + 4 * p + 2));
        }
        Coliseum.corners();
        return true;
    }

    /** An angle in radians as one of a turn's 65536 steps, the way it travels. */
    static int toWire(float angle) {
        return Math.round(angle * (float) (32768 / Math.PI)) & 0xFFFF;
    }

    static float fromWire(int angle) {
        return angle * (float) (Math.PI / 32768);
    }

    static int unsigned(byte[] packet, int offset) {
        return packet[offset] & 0xff;
    }

    static void writeShort(byte[] packet, int offset, int value) {
        packet[offset] = (byte) (value >>> 8);
        packet[offset + 1] = (byte) value;
    }

    /** A signed 16-bit value. */
    static int readShort(byte[] packet, int offset) {
        return (short) (unsigned(packet, offset) << 8 | unsigned(packet, offset + 1));
    }

    /** An unsigned 16-bit value. */
    static int readWord(byte[] packet, int offset) {
        return unsigned(packet, offset) << 8 | unsigned(packet, offset + 1);
    }

    private static void writeInt(byte[] packet, int offset, int value) {
        packet[offset] = (byte) (value >>> 24);
        packet[offset + 1] = (byte) (value >>> 16);
        packet[offset + 2] = (byte) (value >>> 8);
        packet[offset + 3] = (byte) value;
    }
}
