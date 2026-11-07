package io.github.jabrena.juno.remote.lego.rcx;

/**
 * The v1 wire contract of the regulated RCX commands: the opcode registry, the fixed payload layouts, the
 * acknowledgement codes, and the bounded retry of an identical command. Plain Java over {@link RcxLink#exchange}; its
 * buffers are allocated once, here, so a request allocates nothing.
 *
 * <p>Multi-byte values are little-endian. A request carries at most seven argument bytes; the opcode's low three bits
 * give the argument count.
 */
final class RcxRegulatedProtocol {
    static final int OPCODE_STATUS = 0x61;
    static final int OPCODE_STOP = 0x62;
    static final int OPCODE_ARM = 0x63;
    static final int OPCODE_PROFILE = 0x67;
    static final int OPCODE_START = 0x76;
    /** The battery in millivolts; it needs no session, since the battery belongs to the brick. */
    static final int OPCODE_BATTERY = 0x70;

    static final int ACCEPTED = 0;
    static final int DUPLICATE = 1;
    static final int NOT_ARMED = 2;
    static final int BUSY = 3;
    static final int INVALID_SESSION = 4;
    static final int INVALID_ARGUMENT = 5;
    static final int OUT_OF_RANGE = 6;
    static final int ID_CONFLICT = 7;
    static final int STALE_COMMAND = 8;
    static final int UNSUPPORTED = 9;

    /** Returned when no valid reply came after the retries: whether the brick acted on the command is unknown. */
    static final int NO_REPLY = -1;
    /** An identical command is sent at most this many times again after a missing reply. */
    static final int MAX_RETRIES = 2;

    /** Whether the brick accepted a command, or had already accepted this identical one. */
    static boolean accepted(int code) {
        return code == ACCEPTED || code == DUPLICATE;
    }

    static final int STATUS_LEFT_STALL = 0x01;
    static final int STATUS_RIGHT_STALL = 0x02;
    static final int STATUS_LEFT_STALE = 0x04;
    static final int STATUS_RIGHT_STALE = 0x08;
    static final int STATUS_TIMEOUT = 0x10;
    static final int STATUS_EXTERNAL_OVERRIDE = 0x20;
    static final int STATUS_INVALID_CONFIGURATION = 0x40;

    private static final int MAX_ARGUMENTS = 7;
    private static final int ARM_REPLY = 3;
    private static final int PROFILE_REPLY = 1;
    private static final int START_REPLY = 3;
    private static final int STOP_REPLY = 3;
    private static final int STATUS_REPLY = 8;
    private static final int BATTERY_REPLY = 2;

    private final RcxLink link;
    private final int[] arguments = new int[MAX_ARGUMENTS];
    private final int[] reply = new int[STATUS_REPLY];

    /** The protocol version and mapping ID the brick reported in its last {@link #arm} reply. */
    int protocolVersion;
    int mapping;
    /** The fields of the last {@link #status} reply. */
    int statusSession;
    int statusMotion;
    int statusState;
    int statusFaults;
    int statusLeft;
    int statusRight;

    RcxRegulatedProtocol(RcxLink link) {
        this.link = link;
    }

    /** Returns the acknowledgement code of {@code REG_ARM}, or {@link #NO_REPLY}. */
    int arm(int session, int mappingId, int flags) {
        arguments[0] = session;
        arguments[1] = mappingId;
        arguments[2] = flags;
        if (!roundTrip(OPCODE_ARM, 3, ARM_REPLY)) {
            return NO_REPLY;
        }
        protocolVersion = reply[1];
        mapping = reply[2];
        return reply[0];
    }

    /** Returns the acknowledgement code of {@code REG_PROFILE}, or {@link #NO_REPLY}. */
    int profile(int session, int maxSpeed, int acceleration, int tolerance, int timeoutSeconds) {
        arguments[0] = session;
        putWord(1, maxSpeed);
        putWord(3, acceleration);
        arguments[5] = tolerance;
        arguments[6] = timeoutSeconds;
        if (!roundTrip(OPCODE_PROFILE, 7, PROFILE_REPLY)) {
            return NO_REPLY;
        }
        return reply[0];
    }

    /** Returns the acknowledgement code of {@code REG_START}, or {@link #NO_REPLY}. */
    int start(int session, int motion, int leftTarget, int rightTarget) {
        arguments[0] = session;
        arguments[1] = motion;
        putWord(2, leftTarget);
        putWord(4, rightTarget);
        if (!roundTrip(OPCODE_START, 6, START_REPLY)) {
            return NO_REPLY;
        }
        return reply[0];
    }

    /** Returns the acknowledgement code of {@code REG_STOP}, or {@link #NO_REPLY}. */
    int stop(int session, int motion) {
        arguments[0] = session;
        arguments[1] = motion;
        if (!roundTrip(OPCODE_STOP, 2, STOP_REPLY)) {
            return NO_REPLY;
        }
        return reply[0];
    }

    /**
     * Reads {@code REG_BATTERY}: the battery in millivolts, low byte first, or {@link #NO_REPLY} when the brick
     * did not answer. The command takes no arguments.
     */
    int battery() {
        if (!roundTrip(OPCODE_BATTERY, 0, BATTERY_REPLY)) {
            return NO_REPLY;
        }
        return reply[0] | (reply[1] << 8);
    }

    /** Reads {@code REG_STATUS} into the status fields; returns {@code false} when no valid reply came. */
    boolean status(int session) {
        arguments[0] = session;
        if (!roundTrip(OPCODE_STATUS, 1, STATUS_REPLY)) {
            return false;
        }
        statusSession = reply[0];
        statusMotion = reply[1];
        statusState = reply[2];
        statusFaults = reply[3];
        statusLeft = reply[4] | (reply[5] << 8);
        statusRight = reply[6] | (reply[7] << 8);
        return true;
    }

    private void putWord(int index, int value) {
        arguments[index] = value & 0xFF;
        arguments[index + 1] = (value >> 8) & 0xFF;
    }

    /** Sends the arguments already in place and returns true once a reply of exactly {@code replyCount} arrives. */
    private boolean roundTrip(int opcode, int count, int replyCount) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (link.exchange(opcode, arguments, count, reply) == replyCount) {
                return true;
            }
        }
        return false;
    }
}
