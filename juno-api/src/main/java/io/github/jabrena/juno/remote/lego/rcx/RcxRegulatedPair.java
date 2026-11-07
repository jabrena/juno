package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.robotics.RegulatedPair;
import io.github.jabrena.juno.util.Delay;

/**
 * The A and C outputs of an RCX regulated by its own firmware, with their rotation sensors on inputs 1 and 3, driven as
 * one pair: a movement sets both wheels' targets in a single infrared command, and the brick regulates both on its own
 * between commands.
 *
 * <p>The pair owns the session, the motion IDs, the profile and the cached status; its two {@link #left()} and
 * {@link #right()} handles are views over it. Positions are sensor steps (16 per sensor revolution) converted to
 * encoder-shaft degrees using {@code sensorStepsPerMotorRevolution}; a movement smaller than one step cannot be
 * represented and is rejected. Create the pair once at startup and reuse it.
 */
public final class RcxRegulatedPair implements RegulatedPair {
    /** Configuration flag: invert the left motor's direction. */
    public static final int INVERT_LEFT_MOTOR = 0x01;
    /** Configuration flag: invert the right motor's direction. */
    public static final int INVERT_RIGHT_MOTOR = 0x02;
    /** Configuration flag: invert the left rotation sensor's count. */
    public static final int INVERT_LEFT_SENSOR = 0x04;
    /** Configuration flag: invert the right rotation sensor's count. */
    public static final int INVERT_RIGHT_SENSOR = 0x08;
    /** Configuration flag: brake rather than coast when a movement stops or completes. */
    public static final int BRAKE_ON_STOP = 0x10;

    /** The only mapping the v1 firmware defines: motor A with sensor 1 on the left, motor C with sensor 3 on the right. */
    static final int MAPPING_ID = 1;
    static final int LEFT = 0;
    static final int RIGHT = 1;

    private static final int MAX_BYTE = 0xFF;
    private static final int MAX_MOTION_ID = 0xFF;
    private static final int WAIT_MARGIN_MILLIS = 1000;
    private static final int POLL_MILLIS = 20;

    private final RcxRegulatedProtocol protocol;
    private final RcxRegulatedScale scale;
    private final RcxRegulatedProfile profile;
    private final RcxRegulatedPositions positions = new RcxRegulatedPositions();
    private final Clock clock;
    private final int session;
    private final int configurationFlags;
    private final RcxRegulatedMotor left;
    private final RcxRegulatedMotor right;

    private int motion;
    private int lastAcknowledgement;
    private int faults;
    private RcxRegulatedState state = RcxRegulatedState.NOT_ARMED;

    /**
     * Creates a pair for {@code session} (1..255) over an infrared link that has a receiver, since every regulated
     * command is answered. {@code configurationFlags} holds the {@code INVERT_*} and {@link #BRAKE_ON_STOP} bits and the
     * maximum motor power (0..7) in bits 5-7. {@code sensorStepsPerMotorRevolution} is the sensor's steps per 360 degrees
     * of motor shaft.
     */
    public RcxRegulatedPair(RcxLink link, int session, int configurationFlags, int sensorStepsPerMotorRevolution) {
        RcxRegulatedConfiguration configuration = new RcxRegulatedConfiguration(link, session, configurationFlags);
        this.protocol = new RcxRegulatedProtocol(link);
        this.scale = new RcxRegulatedScale(sensorStepsPerMotorRevolution);
        this.profile = new RcxRegulatedProfile(scale);
        this.clock = link.clock();
        this.session = session;
        this.configurationFlags = configurationFlags;
        this.left = new RcxRegulatedMotor(this, LEFT);
        this.right = new RcxRegulatedMotor(this, RIGHT);
        // Claimed last, so a rejected configuration leaves the link free for another pair.
        link.claimForRegulation();
    }

    /**
     * The configuration bits for a maximum motor power of {@code level} (1 to 7, full power is 7), to OR into the
     * configuration flags. A pair with no maximum power would never move, so the constructor rejects one.
     */
    public static int maxPower(int level) {
        if (level < 1 || level > 7) {
            throw new IllegalArgumentException("maximum motor power must be between 1 and 7");
        }
        return level << 5;
    }

    /** The left side, the motor A output, as a {@link RcxRegulatedMotor} view of this pair. */
    public RcxRegulatedMotor left() {
        return left;
    }

    /** The right side, the motor C output, as a {@link RcxRegulatedMotor} view of this pair. */
    public RcxRegulatedMotor right() {
        return right;
    }

    /**
     * Arms the session: the brick reconfigures its sensors, clears its motion history and faults, and reports
     * {@link RcxRegulatedState#READY}. Positions are measured from here, so each side's tachometer starts at zero.
     * Arming is the recovery after a latched fault and needs both outputs safe.
     */
    public void arm() {
        int ack = protocol.arm(session, MAPPING_ID, configurationFlags);
        if (ack != RcxRegulatedProtocol.ACCEPTED) {
            lastAcknowledgement = ack;
            throw new IllegalStateException("REG_ARM was not accepted");
        }
        if (protocol.mapping != MAPPING_ID) {
            throw new IllegalStateException("the brick mapped the pair to another mapping");
        }
        motion = 0;
        if (!refreshStatus()) {
            throw new IllegalStateException("no status reply after REG_ARM");
        }
        positions.restartFromReadings();
    }

    /** Sets the target speed, in encoder-shaft degrees per second, applied to the next movement. */
    @Override
    public void setSpeed(int degreesPerSecond) {
        profile.setSpeed(degreesPerSecond);
    }

    /** Returns the target speed, in encoder-shaft degrees per second. */
    public int speed() {
        return profile.speed();
    }

    /** Sets the acceleration, in encoder-shaft degrees per second squared, applied to the next movement. */
    @Override
    public void setAcceleration(int degreesPerSecondSquared) {
        profile.setAcceleration(degreesPerSecondSquared);
    }

    /** Sets the position tolerance, in sensor steps (0..255): a side within it counts as complete. */
    public void setTolerance(int steps) {
        profile.setTolerance(steps);
    }

    /** Sets the time, in seconds (1..255), a movement may run before the brick times it out. */
    public void setTimeoutSeconds(int seconds) {
        profile.setTimeoutSeconds(seconds);
    }

    /**
     * Starts one coordinated movement: the left and right sides each move relative to their current position, by their
     * degrees, in the same infrared command. Refreshes the status first, sends the profile, then starts. Waits for
     * completion unless {@code immediateReturn} is set.
     *
     * @throws IllegalStateException if the pair is not ready to start, or the brick rejects or cannot answer the start
     * @throws IllegalArgumentException if a side's movement is out of range or quantizes to zero
     */
    @Override
    public void rotate(int leftDegrees, int rightDegrees, boolean immediateReturn) {
        if (!refreshStatus()) {
            throw new IllegalStateException("no status reply; nothing was started");
        }
        if (!state.canStart()) {
            throw new IllegalStateException("the pair is not ready to start; arm it before rotating");
        }
        int leftSteps = scale.steps(leftDegrees);
        int rightSteps = scale.steps(rightDegrees);
        if (motion == MAX_MOTION_ID) {
            throw new IllegalStateException("motion IDs are used up; arm() a new session");
        }
        int leftTarget = positions.target(LEFT, leftSteps);
        int rightTarget = positions.target(RIGHT, rightSteps);
        motion = motion + 1;
        int profileAck = profile.send(protocol, session);
        if (profileAck != RcxRegulatedProtocol.ACCEPTED) {
            lastAcknowledgement = profileAck;
            throw new IllegalStateException("REG_PROFILE was not accepted");
        }
        startMotion(leftTarget, rightTarget);
        if (!immediateReturn) {
            waitComplete();
        }
    }

    /** Stops both sides, qualified by the active motion so a stale stop cannot stop a newer movement. */
    @Override
    public void stop() {
        if (motion == 0) {
            return;
        }
        int ack = protocol.stop(session, motion);
        if (ack == RcxRegulatedProtocol.NO_REPLY) {
            state = RcxRegulatedState.LINK_UNKNOWN;
            throw new IllegalStateException("REG_STOP outcome unknown; the outputs may still be moving");
        }
        if (!RcxRegulatedProtocol.accepted(ack)) {
            lastAcknowledgement = ack;
            throw new IllegalStateException("REG_STOP was not accepted");
        }
        refreshStatus();
    }

    /** Returns the cached state; {@link #status()} refreshes it first. */
    public RcxRegulatedState cachedState() {
        return state;
    }

    /** Reads the brick's status, updates the cached state and positions, and returns the state. */
    public RcxRegulatedState status() {
        refreshStatus();
        return state;
    }

    /** Returns the cached fault bits of the last status read. */
    public int faults() {
        return faults;
    }

    /** Returns the cached state without a round trip: {@code true} while a movement is running. */
    @Override
    public boolean isMoving() {
        return state == RcxRegulatedState.RUNNING;
    }

    /** Returns the cached state: {@code true} when the controller stopped a side because it made no progress. */
    @Override
    public boolean isStalled() {
        return state == RcxRegulatedState.STALLED;
    }

    /**
     * Polls the status until the movement completes or latches an error. Gives up after the movement's timeout plus a
     * margin and reports {@link RcxRegulatedState#LINK_UNKNOWN}, since the brick should have finished by then.
     */
    @Override
    public void waitComplete() {
        long started = clock.millis();
        long limit = profile.timeoutSeconds() * 1000L + WAIT_MARGIN_MILLIS;
        while (state == RcxRegulatedState.RUNNING || state == RcxRegulatedState.LINK_UNKNOWN) {
            refreshStatus();
            if (state != RcxRegulatedState.RUNNING && state != RcxRegulatedState.LINK_UNKNOWN) {
                return;
            }
            if (clock.millis() - started > limit) {
                state = RcxRegulatedState.LINK_UNKNOWN;
                return;
            }
            Delay.millis(POLL_MILLIS);
        }
    }

    /** The side's tachometer in encoder-shaft degrees, measured from its last zero; refreshes the status first. */
    int tachoCount(int side) {
        refreshStatus();
        return scale.degrees(positions.stepsFromZero(side));
    }

    /** Sets the side's tachometer zero to its current position; rejected while the pair moves. */
    void resetTachoCount(int side) {
        refreshStatus();
        positions.zeroHere(side);
    }

    private void startMotion(int leftTarget, int rightTarget) {
        int ack = protocol.start(session, motion, leftTarget, rightTarget);
        if (ack == RcxRegulatedProtocol.NO_REPLY) {
            state = RcxRegulatedState.LINK_UNKNOWN;
            throw new IllegalStateException("REG_START outcome unknown; read status() before moving again");
        }
        if (!RcxRegulatedProtocol.accepted(ack)) {
            lastAcknowledgement = ack;
            throw new IllegalStateException("REG_START was not accepted");
        }
        state = RcxRegulatedState.RUNNING;
    }

    /**
     * Reads {@code REG_STATUS}, records both sides' positions and maps the state. Returns {@code false} when no valid
     * reply came, or the reply belongs to another session; the state then says why.
     */
    private boolean refreshStatus() {
        if (!protocol.status(session)) {
            state = RcxRegulatedState.LINK_UNKNOWN;
            return false;
        }
        if (protocol.statusSession != session) {
            state = RcxRegulatedState.NOT_ARMED;
            return false;
        }
        faults = protocol.statusFaults;
        state = RcxRegulatedState.fromStatus(protocol.statusState, faults);
        positions.record(LEFT, protocol.statusLeft);
        positions.record(RIGHT, protocol.statusRight);
        return true;
    }

    /** The acknowledgement code of the last command the brick refused, as {@link RcxRegulatedProtocol} numbers them. */
    public int lastAcknowledgement() {
        return lastAcknowledgement;
    }
}
