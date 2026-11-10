package io.github.jabrena.juno.remote.lego.rcx;

/**
 * The motion profile of a regulated pair: the speed and acceleration in encoder-shaft degrees, and the tolerance and
 * timeout in the firmware's units. It applies to the next movement; the pair sends it with each start. The brake lead,
 * also in the firmware's units, is sent when the pair is armed.
 */
final class RcxRegulatedProfile {
    private static final int MAX_BYTE = 0xFF;
    private static final int DEFAULT_SPEED = 180;
    private static final int DEFAULT_ACCELERATION = 360;
    private static final int DEFAULT_TOLERANCE = 1;
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    private final RcxRegulatedScale scale;
    private int speedDegrees = DEFAULT_SPEED;
    private int accelerationDegrees = DEFAULT_ACCELERATION;
    private int toleranceSteps = DEFAULT_TOLERANCE;
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    private int brakeLeadCycles = RcxRegulatedPair.DEFAULT_BRAKE_LEAD;

    RcxRegulatedProfile(RcxRegulatedScale scale) {
        this.scale = scale;
    }

    void setSpeed(int degreesPerSecond) {
        scale.rateCounts(degreesPerSecond);
        speedDegrees = degreesPerSecond;
    }

    int speed() {
        return speedDegrees;
    }

    void setAcceleration(int degreesPerSecondSquared) {
        scale.rateCounts(degreesPerSecondSquared);
        accelerationDegrees = degreesPerSecondSquared;
    }

    void setTolerance(int steps) {
        if (steps < 0 || steps > MAX_BYTE) {
            throw new IllegalArgumentException("tolerance must be between 0 and 255 sensor steps");
        }
        toleranceSteps = steps;
    }

    void setTimeoutSeconds(int seconds) {
        if (seconds < 1 || seconds > MAX_BYTE) {
            throw new IllegalArgumentException("timeout must be between 1 and 255 seconds");
        }
        timeoutSeconds = seconds;
    }

    void setBrakeLead(int cycles) {
        if (cycles < 0 || cycles > MAX_BYTE) {
            throw new IllegalArgumentException("brake lead must be between 0 and 255 control cycles");
        }
        brakeLeadCycles = cycles;
    }

    int brakeLead() {
        return brakeLeadCycles;
    }

    /** Sends the brake lead to the brick for {@code session}, returning the acknowledgement code; arming resets it. */
    int sendBrakeLead(RcxRegulatedProtocol protocol, int session) {
        return protocol.brakeLead(session, brakeLeadCycles);
    }

    int timeoutSeconds() {
        return timeoutSeconds;
    }

    /** Sends this profile to the brick for {@code session}, returning the acknowledgement code. */
    int send(RcxRegulatedProtocol protocol, int session) {
        return protocol.profile(session, scale.rateCounts(speedDegrees), scale.rateCounts(accelerationDegrees),
                toleranceSteps, timeoutSeconds);
    }
}
