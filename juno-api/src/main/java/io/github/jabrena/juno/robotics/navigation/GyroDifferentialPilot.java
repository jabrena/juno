package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.robotics.DirectionFinder;
import io.github.jabrena.juno.robotics.EncoderMotor;

/**
 * A {@link Pilot} over two {@link EncoderMotor}s whose power the program sets directly, such as a Powered Up hub's, that
 * closes both loops itself with a {@link DirectionFinder}, a {@link GyroHeading} for instance. Every
 * {@link #setControlPeriodMillis control period}:
 *
 * <ul>
 *   <li><b>Travel:</b> a {@link DifferentialDriveOdometry} keeps the pose from the encoders and the direction, along the
 *       line the robot faced at the start. A {@link HeadingHoldController} steers to a heading that leans back toward
 *       the line, so a drift is taken out, not just stopped from growing, and the travel ends when the progress along
 *       the line reaches the distance. The power ramps up from the minimum to the cruise power and back down near the
 *       end, so the wheels do not coast past.</li>
 *   <li><b>Rotate:</b> the robot spins in place toward the direction it had plus the turn, slowing down near it; it
 *       brakes inside the tolerance, settles, and nudges again, either way, while the settled direction is still outside
 *       it.</li>
 * </ul>
 *
 * <p>With an {@link ObstacleDetector}, a forward travel reads it every {@link #setObstacleCheckMillis obstacle check}
 * and brakes when the way is blocked, ending with {@link MoveOutcome#OBSTACLE}; a rotation and a backward travel do not
 * read it, since the detector looks ahead. A range read can take a while (an ultrasonic ranger waits up to 30 ms for no
 * echo), which is why it is read less often than the motors are commanded.
 *
 * <p>Motors are seen from the robot: forward power drives the wheel forward and the count grows, whichever way a motor
 * is mounted, so wrap a mirrored motor to turn both around. A {@link #stop()} from another thread is seen at the next
 * period. Waiting uses {@link Thread#sleep}, so a {@link GyroHeading}'s thread keeps its turns. Create it once at
 * startup; its moves allocate nothing.
 */
public final class GyroDifferentialPilot implements Pilot {
    private static final int MAX_POWER = 100;

    private final DifferentialDrive drive;
    private final DirectionFinder direction;
    private final DifferentialDriveOdometry odometry;
    private final HeadingHoldController hold;
    private final Clock clock;

    private int cruisePower = 50;
    private int minPower = 25;
    private int rampMillimetres = 150;
    private int turnMaxPower = 35;
    private int turnMinPower = 20;
    private int turnSlowdownDegrees = 30;
    private float turnToleranceDegrees = 1.5f;
    private int turnAttempts = 4;
    /** How far the held heading leans back toward the line per millimetre off it, and at most, in millidegrees. */
    private int lineMillidegreesPerMillimetre = 500;
    private int maxLineMillidegrees = 10_000;
    private int controlPeriodMillis = 20;
    private int settleMillis = 400;
    private int timeoutMillis = 10_000;
    private int obstacleCheckMillis = 60;
    private ObstacleDetector obstacles = new NoObstacles();

    private volatile boolean stopRequested;
    private int travelled;
    private MoveOutcome lastOutcome = MoveOutcome.COMPLETED;

    /**
     * Creates the pilot. {@code geometry} converts the encoders to millimetres; its track width is used only for a
     * heading from the wheels when the direction finder gives none, which a {@link GyroHeading} never does.
     */
    public GyroDifferentialPilot(EncoderMotor left, EncoderMotor right, DriveGeometry geometry, DirectionFinder direction,
            Clock clock) {
        this.drive = new DifferentialDrive(left, right);
        this.direction = direction;
        Pose pose = new Pose();
        this.odometry = new DifferentialDriveOdometry(left, right, direction, geometry, pose);
        // About 3 power per degree of heading error, at most 15: enough to steer, not enough to weave.
        this.hold = new HeadingHoldController(drive, pose, 270, 15);
        this.clock = clock;
    }

    /** The travel's cruise power and the power it starts and ends at, percent; the minimum must still move the robot. */
    public void setTravelPowers(int cruise, int minimum) {
        requirePowers(cruise, minimum);
        cruisePower = cruise;
        minPower = minimum;
    }

    /** The distance over which the travel's power ramps up after the start and down before the end. */
    public void setRampMillimetres(int millimetres) {
        requirePositive(millimetres);
        rampMillimetres = millimetres;
    }

    /** The rotation's power far from the target and near it, percent. */
    public void setTurnPowers(int maximum, int minimum) {
        requirePowers(maximum, minimum);
        turnMaxPower = maximum;
        turnMinPower = minimum;
    }

    /** How close to the target direction a rotation must settle, in degrees. */
    public void setTurnToleranceDegrees(float degrees) {
        if (!(degrees > 0f)) {
            throw new IllegalArgumentException("the tolerance must be positive");
        }
        turnToleranceDegrees = degrees;
    }

    /** How often the pilot reads its sensors and commands the motors; each command may be a radio write. */
    public void setControlPeriodMillis(int millis) {
        requirePositive(millis);
        controlPeriodMillis = millis;
    }

    /** How long a move may take before it ends as failed. */
    public void setTimeoutMillis(int millis) {
        requirePositive(millis);
        timeoutMillis = millis;
    }

    /** Reads {@code detector} during forward travels, and stops for what it sees. */
    public void setObstacleDetector(ObstacleDetector detector) {
        obstacles = detector;
    }

    /** How often a forward travel reads the obstacle detector. */
    public void setObstacleCheckMillis(int millis) {
        requirePositive(millis);
        obstacleCheckMillis = millis;
    }

    /** The odometry's pose of the last travel: x along the line it started on, y to the left of it. */
    public Pose lastTravelPose() {
        return odometry.pose();
    }

    @Override
    public boolean travel(int millimetres) {
        stopRequested = false;
        travelled = 0;
        obstacles.reset();
        Pose pose = odometry.pose();
        odometry.reset();
        // The first update takes the current direction as the pose's heading zero: the line runs along x.
        odometry.update();
        int sign = millimetres >= 0 ? 1 : -1;
        int target = sign * millimetres;
        int started = clock.millis();
        int nextCheck = started;
        boolean arrived = false;
        boolean blocked = false;
        while (!stopRequested && clock.millis() - started < timeoutMillis) {
            odometry.update();
            int progress = sign * pose.xMillimetres();
            int remaining = target - progress;
            if (remaining <= 0) {
                arrived = true;
                break;
            }
            if (sign > 0 && clock.millis() - nextCheck >= 0) {
                nextCheck = clock.millis() + obstacleCheckMillis;
                if (obstacles.blocked()) {
                    blocked = true;
                    break;
                }
            }
            // Left of the line (positive y) leans the held heading clockwise going forward, counter-clockwise going
            // backward, and the other way round.
            hold.setTargetHeadingMillidegrees(
                    clamp(-sign * pose.yMillimetres() * lineMillidegreesPerMillimetre, maxLineMillidegrees));
            hold.holdHeading(sign * rampedPower(Math.min(Math.max(progress, 0), remaining)));
            if (!pause(controlPeriodMillis)) {
                break;
            }
        }
        drive.stop();
        pause(settleMillis);
        odometry.update();
        travelled = pose.xMillimetres();
        lastOutcome = stopRequested ? MoveOutcome.STOPPED
                : arrived ? MoveOutcome.COMPLETED : blocked ? MoveOutcome.OBSTACLE : MoveOutcome.FAILED;
        return lastOutcome == MoveOutcome.COMPLETED;
    }

    @Override
    public boolean rotate(int degrees) {
        stopRequested = false;
        float target = direction.getDegreesCartesian() + degrees;
        lastOutcome = MoveOutcome.FAILED;
        for (int attempt = 0; attempt < turnAttempts; attempt++) {
            if (!spinTo(target) || !pause(settleMillis)) {
                break;
            }
            if (Math.abs(direction.getDegreesCartesian() - target) <= turnToleranceDegrees) {
                lastOutcome = MoveOutcome.COMPLETED;
                break;
            }
        }
        if (stopRequested) {
            lastOutcome = MoveOutcome.STOPPED;
        }
        return lastOutcome == MoveOutcome.COMPLETED;
    }

    @Override
    public void stop() {
        stopRequested = true;
    }

    @Override
    public int travelledMillimetres() {
        return travelled;
    }

    @Override
    public MoveOutcome lastOutcome() {
        return lastOutcome;
    }

    /** Spins toward {@code target} until the direction is inside the tolerance, then brakes; false otherwise. */
    private boolean spinTo(float target) {
        int started = clock.millis();
        while (!stopRequested && clock.millis() - started < timeoutMillis) {
            // Positive: still counter-clockwise of the target, so spin clockwise (left forward, right backward).
            float error = direction.getDegreesCartesian() - target;
            float size = Math.abs(error);
            if (size <= turnToleranceDegrees) {
                drive.stop();
                return true;
            }
            int power = size >= turnSlowdownDegrees ? turnMaxPower
                    : turnMinPower + (int) ((turnMaxPower - turnMinPower) * size / turnSlowdownDegrees);
            int signed = error > 0 ? power : -power;
            drive.tank(signed, -signed);
            if (!pause(controlPeriodMillis)) {
                break;
            }
        }
        drive.stop();
        return false;
    }

    /** The power {@code distance} millimetres from the nearer end of the travel. */
    private int rampedPower(int distance) {
        if (distance >= rampMillimetres) {
            return cruisePower;
        }
        return minPower + (cruisePower - minPower) * distance / rampMillimetres;
    }

    /** Sleeps, letting other threads run; an interrupt counts as a stop. */
    private boolean pause(int millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException interrupted) {
            stopRequested = true;
            return false;
        }
    }

    private static int clamp(int value, int bound) {
        return value > bound ? bound : value < -bound ? -bound : value;
    }

    private static void requirePowers(int high, int low) {
        if (low <= 0 || high < low || high > MAX_POWER) {
            throw new IllegalArgumentException("powers must satisfy 0 < minimum <= maximum <= 100");
        }
    }

    private static void requirePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("the value must be positive");
        }
    }
}
