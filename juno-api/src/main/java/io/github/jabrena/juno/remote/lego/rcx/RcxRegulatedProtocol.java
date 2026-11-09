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
    /**
     * The profile goes in two four-argument commands: the brick's ROM reads an opcode ending in 7 as carrying one
     * argument, so a single seven-argument profile frame never reached the firmware.
     */
    static final int OPCODE_SPEED = 0x64;
    static final int OPCODE_ACCELERATION = 0x74;
    /**
     * A start is three commands: the brick also dropped the six-argument start at 0x76, so each target is staged in a
     * three-argument command and the two-argument start begins both sides at once.
     */
    static final int OPCODE_TARGET_LEFT = 0x33;
    static final int OPCODE_TARGET_RIGHT = 0x43;
    static final int OPCODE_START = 0x52;

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

    private static final int MAX_ARGUMENTS = 4;
    private static final int ARM_REPLY = 3;
    private static final int PROFILE_REPLY = 1;
    private static final int TARGET_REPLY = 1;
    private static final int START_REPLY = 3;
    private static final int STOP_REPLY = 3;
    private static final int STATUS_REPLY = 8;

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

    /**
     * Sends the profile as {@code REG_SPEED} then {@code REG_ACCELERATION}; returns the first acknowledgement that is
     * not {@link #ACCEPTED}, {@link #NO_REPLY} when a half got no reply, or {@link #ACCEPTED} when both were accepted.
     */
    int profile(int session, int maxSpeed, int acceleration, int tolerance, int timeoutSeconds) {
        int speed = profileHalf(OPCODE_SPEED, session, maxSpeed, tolerance);
        if (speed != ACCEPTED) {
            return speed;
        }
        return profileHalf(OPCODE_ACCELERATION, session, acceleration, timeoutSeconds);
    }

    /** One four-argument profile command: session, a rate word (low byte first), then one byte. */
    private int profileHalf(int opcode, int session, int rate, int last) {
        arguments[0] = session;
        putWord(1, rate);
        arguments[3] = last;
        if (!roundTrip(opcode, 4, PROFILE_REPLY)) {
            return NO_REPLY;
        }
        return reply[0];
    }

    /**
     * Stages both targets, then sends {@code REG_START}; returns the first acknowledgement that is not {@link #ACCEPTED},
     * or {@link #NO_REPLY} when a command got no reply.
     */
    int start(int session, int motion, int leftTarget, int rightTarget) {
        int left = stageTarget(OPCODE_TARGET_LEFT, session, leftTarget);
        if (left != ACCEPTED) {
            return left;
        }
        int right = stageTarget(OPCODE_TARGET_RIGHT, session, rightTarget);
        if (right != ACCEPTED) {
            return right;
        }
        arguments[0] = session;
        arguments[1] = motion;
        if (!roundTrip(OPCODE_START, 2, START_REPLY)) {
            return NO_REPLY;
        }
        return reply[0];
    }

    /** Stages one side's target for the next start: session, then the target word, low byte first. */
    private int stageTarget(int opcode, int session, int target) {
        arguments[0] = session;
        putWord(1, target);
        if (!roundTrip(opcode, 3, TARGET_REPLY)) {
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
