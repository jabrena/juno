package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.robotics.navigation.DriveGeometry;
import io.github.jabrena.juno.robotics.navigation.RegulatedDifferentialDrive;

/**
 * A two-wheel RCX vehicle whose wheels are regulated on the brick: one {@link RcxRegulatedPair} on the firmware's only
 * mapping (motor A with sensor 1 as the pair's left side, motor C with sensor 3 as its right side) driven by distance and
 * heading through a {@link RegulatedDifferentialDrive}. It brings the RCX session, the regulator settings and the
 * geometry together so a program configures one object, arms it, and moves it.
 *
 * <p>Create it once at startup, set the regulator ({@link #setTolerance}, {@link #setTimeoutSeconds},
 * {@link #setBrakeLead}, {@link #setLinearSpeedMillimetresPerSecond}), then call {@link #tryArm()} until the brick
 * accepts the session. The settings the brick receives on arming apply from the next arm. Diagnostics, tachometers and
 * the per-side motors stay on {@link #pair()}; {@link #drive()} is the board-independent drive, for composing it with
 * other navigation such as a gyro-regulated drive.
 */
public final class RcxRegulatedDifferentialDrive {
    private final RcxRegulatedPair pair;
    private final RegulatedDifferentialDrive drive;

    /**
     * Creates the drive over {@code link} for {@code session} (1..255), with the pair's {@code configurationFlags} (its
     * {@code INVERT_*} and {@link RcxRegulatedPair#BRAKE_ON_STOP} bits and {@link RcxRegulatedPair#maxPower(int)}), the
     * rotation sensor's steps per motor revolution, and the measured {@code geometry}. The pair's left side drives the
     * robot's left wheel.
     */
    public RcxRegulatedDifferentialDrive(RcxLink link, int session, int configurationFlags,
            int sensorStepsPerMotorRevolution, DriveGeometry geometry) {
        this(link, session, configurationFlags, sensorStepsPerMotorRevolution, geometry, false);
    }

    /**
     * Creates the drive as {@link #RcxRegulatedDifferentialDrive(RcxLink, int, int, int, DriveGeometry)} does; with
     * {@code sidesSwapped}, the pair's left side (motor A) drives the robot's right wheel, so a counter-clockwise turn
     * still turns counter-clockwise.
     */
    public RcxRegulatedDifferentialDrive(RcxLink link, int session, int configurationFlags,
            int sensorStepsPerMotorRevolution, DriveGeometry geometry, boolean sidesSwapped) {
        this.pair = new RcxRegulatedPair(link, session, configurationFlags, sensorStepsPerMotorRevolution);
        this.drive = new RegulatedDifferentialDrive(pair, geometry, sidesSwapped);
    }

    /** The regulated pair under this drive: its diagnostics, tachometers, state and per-side motors. */
    public RcxRegulatedPair pair() {
        return pair;
    }

    /** The board-independent drive over the pair, for composing with other navigation. */
    public RegulatedDifferentialDrive drive() {
        return drive;
    }

    /** Sets the position tolerance, in sensor steps (0..255): a wheel within it counts as complete. */
    public void setTolerance(int steps) {
        pair.setTolerance(steps);
    }

    /** Sets the time, in seconds (1..255), a movement may run before the brick times it out. */
    public void setTimeoutSeconds(int seconds) {
        pair.setTimeoutSeconds(seconds);
    }

    /** Sets the brake lead, in the brick's control cycles (0..255); it applies from the next arm. */
    public void setBrakeLead(int cycles) {
        pair.setBrakeLead(cycles);
    }

    /** Sets the forward speed, in millimetres per second, of the movements that follow. */
    public void setLinearSpeedMillimetresPerSecond(int millimetresPerSecond) {
        drive.setLinearSpeedMillimetresPerSecond(millimetresPerSecond);
    }

    /**
     * Arms the session and reports whether the brick accepted it. A refusal or no answer (the brick off, out of infrared
     * range or not running the regulator firmware) returns {@code false}, so the caller can retry;
     * {@code pair().lastAcknowledgement()} tells which.
     */
    public boolean tryArm() {
        try {
            pair.arm();
            return true;
        } catch (IllegalStateException refused) {
            return false;
        }
    }

    /** Drives straight forward (or backward, when negative) by {@code millimetres}, and waits until it completes. */
    public void travelMillimetres(int millimetres) {
        drive.travelMillimetres(millimetres);
    }

    /** Drives straight by {@code millimetres}, returning at once when {@code immediateReturn} is set. */
    public void travelMillimetres(int millimetres, boolean immediateReturn) {
        drive.travelMillimetres(millimetres, immediateReturn);
    }

    /** Turns in place by {@code degrees} (positive is counter-clockwise), and waits until it completes. */
    public void turnDegrees(int degrees) {
        drive.turnDegrees(degrees);
    }

    /** Turns in place by {@code degrees}, returning at once when {@code immediateReturn} is set. */
    public void turnDegrees(int degrees, boolean immediateReturn) {
        drive.turnDegrees(degrees, immediateReturn);
    }

    /** Turns about one wheel by {@code degrees} (positive is counter-clockwise), and waits until it completes. */
    public void pivotDegrees(int degrees) {
        drive.pivotDegrees(degrees);
    }

    /** Turns about one wheel by {@code degrees}, returning at once when {@code immediateReturn} is set. */
    public void pivotDegrees(int degrees, boolean immediateReturn) {
        drive.pivotDegrees(degrees, immediateReturn);
    }

    /** Stops both wheels. */
    public void stop() {
        drive.stop();
    }

    /** Returns whether a movement is in progress. */
    public boolean isMoving() {
        return drive.isMoving();
    }

    /** Waits until the current movement completes or reports an error state. */
    public void waitComplete() {
        drive.waitComplete();
    }

    /** Polls the brick and returns the state the current or last movement is in. */
    public RcxRegulatedState status() {
        return pair.status();
    }
}
