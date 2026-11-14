package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.robotics.DirectionFinder;
import io.github.jabrena.juno.robotics.EncoderMotor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("GyroDifferentialPilot: encoder motors steered by a gyroscope")
class GyroDifferentialPilotTest {
    private static final int WHEEL_DIAMETER = 82;
    private static final int TRACK = 150;

    /**
     * A two-wheel robot moving in real time: each wheel's speed is its power times {@link #MM_PER_SECOND_PER_POWER}
     * times its gain, and it stops dead when braked, or {@link #stopLagMillis} later, as a robot behind a radio link does.
     * Reading any sensor advances it to now.
     */
    private static final class SimulatedRobot implements Clock, DirectionFinder {
        static final double MM_PER_SECOND_PER_POWER = 20.0;
        final long startNanos = System.nanoTime();
        long lastNanos = startNanos;
        long stopLagMillis;
        /** When the pending stop takes effect, or {@code -1} with none pending. */
        long brakeAtNanos = -1;
        double x;
        double y;
        double headingRadians;
        double leftMillimetres;
        double rightMillimetres;
        double leftGain = 1.0;
        double rightGain = 1.0;
        final Wheel left = new Wheel(true);
        final Wheel right = new Wheel(false);

        synchronized void advance() {
            long now = System.nanoTime();
            if (brakeAtNanos >= 0 && now >= brakeAtNanos) {
                integrate(brakeAtNanos);
                left.landStop();
                right.landStop();
                brakeAtNanos = -1;
            }
            integrate(now);
        }

        private void integrate(long until) {
            double seconds = (until - lastNanos) / 1e9;
            lastNanos = until;
            double vl = left.signedPower * MM_PER_SECOND_PER_POWER * leftGain;
            double vr = right.signedPower * MM_PER_SECOND_PER_POWER * rightGain;
            leftMillimetres += vl * seconds;
            rightMillimetres += vr * seconds;
            double forward = (vl + vr) / 2 * seconds;
            double turn = (vr - vl) / TRACK * seconds;
            x += forward * Math.cos(headingRadians + turn / 2);
            y += forward * Math.sin(headingRadians + turn / 2);
            headingRadians += turn;
        }

        @Override
        public int millis() {
            return (int) ((System.nanoTime() - startNanos) / 1_000_000);
        }

        @Override
        public int micros() {
            return (int) ((System.nanoTime() - startNanos) / 1_000);
        }

        @Override
        public synchronized float getDegreesCartesian() {
            advance();
            return (float) Math.toDegrees(headingRadians);
        }

        @Override
        public void resetCartesianZero() {
        }

        @Override
        public void startCalibration() {
        }

        @Override
        public void stopCalibration() {
        }

        final class Wheel implements EncoderMotor {
            private final boolean isLeft;
            private int power;
            int signedPower;
            private double zero;
            private boolean stopping;

            Wheel(boolean isLeft) {
                this.isLeft = isLeft;
            }

            private double millimetres() {
                advance();
                return isLeft ? leftMillimetres : rightMillimetres;
            }

            @Override
            public void setPower(int power) {
                this.power = power;
            }

            @Override
            public int getPower() {
                return power;
            }

            @Override
            public void forward() {
                advance();
                stopping = false;
                signedPower = power;
            }

            @Override
            public void backward() {
                advance();
                stopping = false;
                signedPower = -power;
            }

            @Override
            public void stop() {
                advance();
                if (stopLagMillis == 0) {
                    signedPower = 0;
                } else {
                    stopping = true;
                    brakeAtNanos = System.nanoTime() + stopLagMillis * 1_000_000;
                }
            }

            /** A stop sent {@link #stopLagMillis} ago reaches the wheel, unless a newer command came first. */
            void landStop() {
                if (stopping) {
                    signedPower = 0;
                    stopping = false;
                }
            }

            @Override
            public void flt() {
                stop();
            }

            @Override
            public boolean isMoving() {
                return signedPower != 0;
            }

            @Override
            public int getTachoCount() {
                return (int) Math.round((millimetres() - zero) * 360 / (Math.PI * WHEEL_DIAMETER));
            }

            @Override
            public void resetTachoCount() {
                zero = millimetres();
            }
        }
    }

    private static GyroDifferentialPilot pilot(SimulatedRobot robot) {
        GyroDifferentialPilot pilot = new GyroDifferentialPilot(robot.left, robot.right,
                new DriveGeometry(WHEEL_DIAMETER, TRACK, 360, 360), robot, robot);
        pilot.setControlPeriodMillis(5);
        pilot.setTimeoutMillis(5000);
        return pilot;
    }

    @Test
    @DisplayName("A travel with a weaker left motor still ends on the line, at the distance")
    void shouldHoldTheLineWithAWeakerMotor() {
        // Given: the left wheel runs 15% slow, which alone would curve the robot to the left
        SimulatedRobot robot = new SimulatedRobot();
        robot.leftGain = 0.85;
        GyroDifferentialPilot pilot = pilot(robot);

        // When
        boolean completed = pilot.travel(400);

        // Then
        assertThat(completed).isTrue();
        assertThat(robot.x).isCloseTo(400, within(15.0));
        assertThat(robot.y).isCloseTo(0, within(15.0));
        assertThat(pilot.travelledMillimetres()).isCloseTo(400, within(15));
    }

    @Test
    @DisplayName("A travel reports how far left of its line it ended, as its odometry measured it")
    void shouldReportTheMeasuredOffsetFromTheLine() {
        // Given: a weaker left motor, steered gently, so the robot ends a little off its line
        SimulatedRobot robot = new SimulatedRobot();
        robot.leftGain = 0.85;
        GyroDifferentialPilot pilot = pilot(robot);
        pilot.setSteering(135, 8);
        pilot.setLineCorrection(200, 5000);
        pilot.setTurnSlowdownDegrees(45);

        // When
        boolean completed = pilot.travel(400);

        // Then: the offset is the odometry's, and matches where the robot is
        assertThat(completed).isTrue();
        assertThat(pilot.travelledLeftMillimetres()).isEqualTo(pilot.lastTravelPose().yMillimetres());
        assertThat(pilot.travelledLeftMillimetres()).isCloseTo((int) Math.round(robot.y), within(3));
    }

    @Test
    @DisplayName("A turn whose stop arrives late brakes ahead of the target with the brake lead, and settles")
    void shouldBrakeAheadOfTheTargetWhenTheStopArrivesLate() {
        // Given: the stop reaches the wheels 160 ms after it is sent, as over the hub's radio
        SimulatedRobot robot = new SimulatedRobot();
        robot.stopLagMillis = 160;
        GyroDifferentialPilot pilot = pilot(robot);
        pilot.setTurnPowers(20, 10);
        // Keep the wall-clock simulation below the radio lag without relying on scheduler-perfect short sleeps.
        pilot.setControlPeriodMillis(40);
        pilot.setTurnSlowdownDegrees(60);
        pilot.setTurnToleranceDegrees(3f);
        pilot.setTurnBrakeLeadMillis(160);

        // When
        boolean completed = pilot.rotate(-90);

        // Then
        assertThat(completed).as("heading was %.3f degrees", Math.toDegrees(robot.headingRadians)).isTrue();
        assertThat(Math.toDegrees(robot.headingRadians)).isCloseTo(-90, within(3.0));
    }

    @Test
    @DisplayName("Steering, line correction and slow-down settings reject values that cannot work")
    void shouldRejectImpossibleSteeringSettings() {
        // Given
        GyroDifferentialPilot pilot = pilot(new SimulatedRobot());

        // Then
        assertThatThrownBy(() -> pilot.setSteering(-1, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pilot.setSteering(135, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pilot.setLineCorrection(-1, 5000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pilot.setTurnSlowdownDegrees(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pilot.setTurnBrakeLeadMillis(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A backward travel drives the robot back along its line")
    void shouldTravelBackward() {
        // Given
        SimulatedRobot robot = new SimulatedRobot();
        GyroDifferentialPilot pilot = pilot(robot);

        // When
        boolean completed = pilot.travel(-200);

        // Then
        assertThat(completed).isTrue();
        assertThat(robot.x).isCloseTo(-200, within(15.0));
        assertThat(pilot.travelledMillimetres()).isCloseTo(-200, within(15));
    }

    @Test
    @DisplayName("A right turn ends inside the tolerance of -90 degrees")
    void shouldTurnRightByTheGyroscope() {
        // Given
        SimulatedRobot robot = new SimulatedRobot();
        GyroDifferentialPilot pilot = pilot(robot);

        // When
        boolean completed = pilot.rotate(-90);

        // Then
        assertThat(completed).isTrue();
        assertThat(Math.toDegrees(robot.headingRadians)).isCloseTo(-90, within(1.5));
    }

    @Test
    @DisplayName("A stop from another thread ends the travel early")
    void shouldEndATravelOnAStopFromAnotherThread() throws InterruptedException {
        // Given
        SimulatedRobot robot = new SimulatedRobot();
        GyroDifferentialPilot pilot = pilot(robot);
        Thread stopper = Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                return;
            }
            pilot.stop();
        });

        // When
        boolean completed = pilot.travel(2000);
        stopper.join();

        // Then
        assertThat(completed).isFalse();
        assertThat(pilot.travelledMillimetres()).isBetween(1, 1000);
    }

    /** A wall across the way at {@code wallX}, seen by a sensor at the robot's centre looking along +x. */
    private static ObstacleDetector wallAt(SimulatedRobot robot, int wallX, int stopMillimetres) {
        return new ObstacleDetector() {
            private int range = -1;

            @Override
            public boolean blocked() {
                robot.advance();
                range = (int) Math.round(wallX - robot.x);
                return range < stopMillimetres;
            }

            @Override
            public int rangeMillimetres() {
                return range;
            }

            @Override
            public void reset() {
            }
        };
    }

    @Test
    @DisplayName("A forward travel brakes short of a wall and ends with OBSTACLE")
    void shouldStopShortOfAWall() {
        // Given: a wall 400 mm ahead, stop at 150 mm
        SimulatedRobot robot = new SimulatedRobot();
        GyroDifferentialPilot pilot = pilot(robot);
        pilot.setObstacleDetector(wallAt(robot, 400, 150));
        pilot.setObstacleCheckMillis(5);

        // When
        boolean completed = pilot.travel(1000);

        // Then
        assertThat(completed).isFalse();
        assertThat(pilot.lastOutcome()).isEqualTo(MoveOutcome.OBSTACLE);
        assertThat(robot.x).isBetween(230.0, 280.0);
        assertThat(pilot.travelledMillimetres()).isBetween(230, 280);
    }

    @Test
    @DisplayName("A backward travel does not read the forward-looking detector")
    void shouldNotReadTheDetectorGoingBackward() {
        // Given: a wall right ahead
        SimulatedRobot robot = new SimulatedRobot();
        GyroDifferentialPilot pilot = pilot(robot);
        pilot.setObstacleDetector(wallAt(robot, 50, 150));

        // When
        boolean completed = pilot.travel(-150);

        // Then
        assertThat(completed).isTrue();
        assertThat(pilot.lastOutcome()).isEqualTo(MoveOutcome.COMPLETED);
    }

    @Test
    @DisplayName("A robot that does not move times out")
    void shouldTimeOutWhenTheRobotDoesNotMove() {
        // Given: both wheels stalled
        SimulatedRobot robot = new SimulatedRobot();
        robot.leftGain = 0;
        robot.rightGain = 0;
        GyroDifferentialPilot pilot = pilot(robot);
        pilot.setTimeoutMillis(200);

        // When & Then
        assertThat(pilot.travel(100)).isFalse();
    }

    @Test
    @DisplayName("A navigator drives a square with the pilot and ends near where it started")
    void shouldDriveASquareWithTheNavigator() {
        // Given
        SimulatedRobot robot = new SimulatedRobot();
        robot.leftGain = 0.9;
        Navigator navigator = new Navigator(pilot(robot), robot, new Waypoint[4]);
        navigator.addWaypoint(new Waypoint(300, 0));
        navigator.addWaypoint(new Waypoint(300, -300));
        navigator.addWaypoint(new Waypoint(0, -300));
        navigator.addWaypoint(new Waypoint(0, 0, 0));

        // When
        navigator.followPath();
        boolean completed = navigator.waitForStop();

        // Then
        assertThat(completed).isTrue();
        assertThat(Math.hypot(robot.x, robot.y)).isLessThanOrEqualTo(30.0);
        assertThat(Pose.shortestTurn((int) Math.round(Math.toDegrees(robot.headingRadians) * 1000))).isCloseTo(0,
                within(3000));
    }
}
