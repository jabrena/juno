package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.RegulatedPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Gyroscope-corrected regulated differential drive")
class GyroRegulatedDifferentialDriveTest {
    /** The reference RCX build: 82 mm wheels, a 160 mm track, the sensor on each wheel's axle. */
    private static final DriveGeometry BUILD = new DriveGeometry(82, 160, 360, 360);
    private static final int SEGMENT = 250;
    private static final int CORRECTION = 3;

    /**
     * A robot on the floor: each move turns it by what its wheels' degrees make on {@link #BUILD}, plus a clockwise
     * drift for every straight move, and its gyroscope reports the turn through a {@link GyroHeading}.
     */
    private static final class SimulatedRobot implements RegulatedPair {
        final GyroHeadingTest.FakeGyroscope gyroscope = new GyroHeadingTest.FakeGyroscope();
        final GyroHeadingTest.FakeClock clock = new GyroHeadingTest.FakeClock();
        final GyroHeading heading = new GyroHeading(gyroscope, clock);
        final List<int[]> moves = new ArrayList<>();
        double driftPerStraightDegrees;
        double extraTurnDegrees;
        /** The move that fails as a stalled or unanswered brick would, counted from zero; -1 for none. */
        int failingMove = -1;
        /** Called after each move, so a test can act between them. */
        Runnable afterMove = () -> {
        };

        /** Polls a move started without waiting takes; each moves the wheels a tenth of the way. */
        static final int POLLS_PER_MOVE = 10;
        int leftTacho;
        int rightTacho;
        int pendingLeft;
        int pendingRight;
        int pendingPolls;

        @Override
        public void rotate(int left, int right, boolean immediateReturn) {
            if (moves.size() == failingMove) {
                moves.add(new int[] {left, right});
                throw new IllegalStateException("stalled");
            }
            if (immediateReturn) {
                moves.add(new int[] {left, right});
                pendingLeft = left;
                pendingRight = right;
                pendingPolls = POLLS_PER_MOVE;
                return;
            }
            leftTacho += left;
            rightTacho += right;
            turnBy(left, right);
        }

        @Override
        public boolean pollMoving() {
            if (pendingPolls == 0) {
                return false;
            }
            leftTacho += pendingLeft / POLLS_PER_MOVE;
            rightTacho += pendingRight / POLLS_PER_MOVE;
            pendingPolls--;
            if (pendingPolls == 0) {
                // The last tenth carries what the division left over.
                leftTacho += pendingLeft % POLLS_PER_MOVE;
                rightTacho += pendingRight % POLLS_PER_MOVE;
                turnBy(pendingLeft, pendingRight);
                moves.remove(moves.size() - 1);
                return false;
            }
            return true;
        }

        @Override
        public int leftTachoCount() {
            return leftTacho;
        }

        @Override
        public int rightTachoCount() {
            return rightTacho;
        }

        /** What a completed move turns the robot by, as its gyroscope sees it. */
        private void turnBy(int left, int right) {
            moves.add(new int[] {left, right});
            double millimetresPerDegree = Math.PI * BUILD.wheelDiameterMillimetres() / 360.0;
            double counterClockwise = Math.toDegrees((right - left) * millimetresPerDegree / BUILD.trackWidthMillimetres());
            double clockwise = -counterClockwise;
            if (left == right) {
                clockwise += driftPerStraightDegrees;
            } else if (left != 0 && right != 0) {
                clockwise += Math.signum(clockwise) * extraTurnDegrees;
            }
            // One second at the turn's rate: the heading's next read adds exactly the turn.
            gyroscope.degreesPerSecond = (float) clockwise;
            clock.millis += 1000;
            heading.sample();
            gyroscope.degreesPerSecond = 0f;
            afterMove.run();
        }

        int pivots() {
            return (int) moves.stream().filter(move -> move[0] == 0 || move[1] == 0).count();
        }

        @Override
        public void setSpeed(int degreesPerSecond) {
        }

        @Override
        public void setAcceleration(int degreesPerSecondSquared) {
        }

        @Override
        public void stop() {
            pendingPolls = 0;
        }

        @Override
        public boolean isMoving() {
            return false;
        }

        @Override
        public boolean isStalled() {
            return false;
        }

        @Override
        public void waitComplete() {
            while (pollMoving()) {
                // Until the move started without waiting ends.
            }
        }
    }

    private static GyroRegulatedDifferentialDrive drive(SimulatedRobot robot) {
        return new GyroRegulatedDifferentialDrive(new RegulatedDifferentialDrive(robot, BUILD), robot.heading, SEGMENT,
                CORRECTION, 0);
    }

    @Test
    @DisplayName("100 cm straight runs as four segments, each followed by a pivot that takes out its drift")
    void shouldHoldTheHeadingOverAStraightBySegments() {
        // Given: every straight segment drifts 4 degrees clockwise
        SimulatedRobot robot = new SimulatedRobot();
        robot.driftPerStraightDegrees = 4;
        GyroRegulatedDifferentialDrive drive = drive(robot);

        // When
        drive.travelMillimetres(1000);

        // Then: four 250 mm moves (349 degrees a wheel), four counter-clockwise pivots on the right wheel, and the robot
        // within the correction threshold of where it started
        assertThat(robot.moves.stream().filter(move -> move[0] == move[1])).hasSize(4)
                .allSatisfy(move -> assertThat(move[0]).isEqualTo(349));
        assertThat(robot.pivots()).isEqualTo(4);
        assertThat(robot.moves.get(1)[0]).isZero();
        assertThat(robot.moves.get(1)[1]).isPositive();
        assertThat(drive.corrections()).isEqualTo(4);
        assertThat(Math.abs(robot.heading.getDegreesCartesian())).isLessThan(CORRECTION);
    }

    @Test
    @DisplayName("A drift below the threshold is left, since a pivot would round to nothing")
    void shouldLeaveASmallErrorAlone() {
        SimulatedRobot robot = new SimulatedRobot();
        robot.driftPerStraightDegrees = 1;
        GyroRegulatedDifferentialDrive drive = drive(robot);

        drive.travelMillimetres(SEGMENT);

        assertThat(robot.pivots()).isZero();
        // 1 degree clockwise is -1 Cartesian
        assertThat(drive.lastErrorDegrees()).isEqualTo(-1);
    }

    @Test
    @DisplayName("A turn is left to the encoders: the gyroscope's error is reported, not corrected, and the turned heading held")
    void shouldHoldTheTurnedHeadingWithoutCorrectingIt() {
        // Given: the gyroscope sees the turn 6 degrees past the 90 asked
        SimulatedRobot robot = new SimulatedRobot();
        robot.extraTurnDegrees = 6;
        GyroRegulatedDifferentialDrive drive = drive(robot);

        // When: a right turn is clockwise, negative for the drive
        drive.turnDegrees(-90);

        // Then: no pivot; the error is reported and the 96 degrees reached become the heading held
        assertThat(robot.pivots()).isZero();
        assertThat(drive.corrections()).isZero();
        // Cartesian: the right turn asked is -90, the gyroscope saw -96
        assertThat(drive.lastErrorDegrees()).isEqualTo(-6);
        assertThat(drive.targetDegrees()).isEqualTo(-96);
    }

    @Test
    @DisplayName("With turns corrected, a right turn 6 degrees too far is pivoted back to the heading asked")
    void shouldCorrectATurnWhenAsked() {
        // Given: the gyroscope sees the turn 6 degrees past the 90 asked
        SimulatedRobot robot = new SimulatedRobot();
        robot.extraTurnDegrees = 6;
        GyroRegulatedDifferentialDrive drive = drive(robot);
        drive.setCorrectTurns(true);

        // When
        drive.turnDegrees(-90);

        // Then: one counter-clockwise pivot brings it back within the threshold of -90
        assertThat(drive.lastErrorDegrees()).isEqualTo(-6);
        assertThat(drive.corrections()).isEqualTo(1);
        assertThat(robot.pivots()).isEqualTo(1);
        assertThat(drive.targetDegrees()).isEqualTo(-90);
        assertThat(Math.abs(robot.heading.getDegreesCartesian() + 90)).isLessThan(CORRECTION);
    }

    @Test
    @DisplayName("A straight after a turn holds the heading the turn reached")
    void shouldKeepTheHeldHeadingAcrossMoves() {
        SimulatedRobot robot = new SimulatedRobot();
        robot.driftPerStraightDegrees = 5;
        GyroRegulatedDifferentialDrive drive = drive(robot);

        drive.travelMillimetres(500);
        drive.turnDegrees(-90);
        drive.travelMillimetres(250);

        // The turn is held as the gyroscope saw it, and the straight after it keeps that heading
        int turned = drive.targetDegrees();
        assertThat(turned).isBetween(-92, -88);
        assertThat(Math.abs(robot.heading.getDegreesCartesian() - turned)).isLessThan(CORRECTION);
    }

    @Test
    @DisplayName("A pivot moves one wheel by twice an in-place turn's degrees, the right one counter-clockwise")
    void shouldPivotAboutOneWheel() {
        SimulatedRobot robot = new SimulatedRobot();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(robot, BUILD);

        drive.pivotDegrees(10);
        drive.pivotDegrees(-10);

        // An in-place 10 degrees is 20 wheel degrees a side; a pivot 40 on one
        assertThat(robot.moves.get(0)).containsExactly(0, 40);
        assertThat(robot.moves.get(1)).containsExactly(40, 0);
    }

    @Test
    @DisplayName("A segment, a threshold or a settle time out of range is refused")
    void shouldRejectInvalidSettings() {
        SimulatedRobot robot = new SimulatedRobot();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(robot, BUILD);

        assertThatThrownBy(() -> new GyroRegulatedDifferentialDrive(drive, robot.heading, 0, CORRECTION, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GyroRegulatedDifferentialDrive(drive, robot.heading, SEGMENT, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GyroRegulatedDifferentialDrive(drive, robot.heading, SEGMENT, CORRECTION, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("As a pilot, a travel reports the distance its segments covered")
    void shouldReportTheTravelAsAPilot() {
        // Given
        SimulatedRobot robot = new SimulatedRobot();
        Pilot pilot = drive(robot);

        // When
        boolean completed = pilot.travel(600);

        // Then: 250 + 250 + 100
        assertThat(completed).isTrue();
        assertThat(pilot.travelledMillimetres()).isEqualTo(600);
    }

    @Test
    @DisplayName("As a pilot, a stalled segment ends the travel as failed, counting only the segments before it")
    void shouldReportAFailedSegmentAsAPilot() {
        // Given: the second straight move stalls
        SimulatedRobot robot = new SimulatedRobot();
        robot.failingMove = 1;
        Pilot pilot = drive(robot);

        // When
        boolean completed = pilot.travel(1000);

        // Then
        assertThat(completed).isFalse();
        assertThat(pilot.travelledMillimetres()).isEqualTo(SEGMENT);
    }

    @Test
    @DisplayName("As a pilot, a stop ends the travel after the segment in progress")
    void shouldStopAfterTheSegmentInProgress() {
        // Given: stop is asked during the first segment
        SimulatedRobot robot = new SimulatedRobot();
        GyroRegulatedDifferentialDrive drive = drive(robot);
        robot.afterMove = drive::stop;

        // When
        boolean completed = drive.travel(1000);

        // Then
        assertThat(completed).isFalse();
        assertThat(drive.travelledMillimetres()).isEqualTo(SEGMENT);
    }

    @Test
    @DisplayName("As a pilot, a turn finer than the wheels can make is not made, and counts as done")
    void shouldSkipATurnBelowTheCorrectionThreshold() {
        // Given
        SimulatedRobot robot = new SimulatedRobot();
        Pilot pilot = drive(robot);

        // When
        boolean completed = pilot.rotate(CORRECTION - 1);

        // Then
        assertThat(completed).isTrue();
        assertThat(robot.moves).isEmpty();
    }

    @Test
    @DisplayName("A navigator drives a square with the RCX pilot, its turns corrected by the gyroscope")
    void shouldDriveASquareWithTheNavigator() {
        // Given: straights drift and turns overshoot, as on the floor
        SimulatedRobot robot = new SimulatedRobot();
        robot.driftPerStraightDegrees = 2;
        robot.extraTurnDegrees = 6;
        GyroRegulatedDifferentialDrive drive = drive(robot);
        drive.setCorrectTurns(true);
        Navigator navigator = new Navigator(drive, robot.heading, new Waypoint[4]);
        navigator.setPositionErrorMillimetres(30);
        navigator.setPose(0, 0, 0);
        navigator.addWaypoint(new Waypoint(500, 0));
        navigator.addWaypoint(new Waypoint(500, -500));
        navigator.addWaypoint(new Waypoint(0, -500));
        navigator.addWaypoint(new Waypoint(0, 0, 0));

        // When
        while (navigator.navigateOnce()) {
            // Until the path is empty or interrupted.
        }

        // Then
        assertThat(navigator.pathCompleted()).isTrue();
        assertThat(Math.hypot(navigator.getPose().xMillimetres(), navigator.getPose().yMillimetres()))
                .isLessThanOrEqualTo(30.0);
        assertThat(Math.abs(Pose.shortestTurn(navigator.getPose().headingMillidegrees()))).isLessThan(CORRECTION * 1000);
    }

    /** A detector that sees the way blocked from its {@code blockedFrom}-th reading on. */
    private static ObstacleDetector blockedAfter(int blockedFrom) {
        return new ObstacleDetector() {
            private int readings;

            @Override
            public boolean blocked() {
                readings++;
                return readings >= blockedFrom;
            }

            @Override
            public int rangeMillimetres() {
                return 200;
            }

            @Override
            public void reset() {
                readings = 0;
            }
        };
    }

    @Test
    @DisplayName("With a detector, an obstacle stops the pair mid-segment, and the travel reports the distance measured")
    void shouldStopMidSegmentForAnObstacle() {
        // Given: the way is blocked at the detector's fourth reading, four tenths into the first 250 mm segment
        SimulatedRobot robot = new SimulatedRobot();
        GyroRegulatedDifferentialDrive drive = drive(robot);
        drive.setObstacleDetector(blockedAfter(4));

        // When
        boolean completed = drive.travel(600);

        // Then: about 100 mm, measured by the tachometers, and no second segment
        assertThat(completed).isFalse();
        assertThat(drive.lastOutcome()).isEqualTo(MoveOutcome.OBSTACLE);
        assertThat(drive.travelledMillimetres()).isBetween(95, 105);
        assertThat(robot.leftTacho).isLessThan(349);
    }

    @Test
    @DisplayName("With a detector that never sees anything, the segments complete as without one")
    void shouldCompleteTheSegmentsWhenTheWayIsClear() {
        // Given
        SimulatedRobot robot = new SimulatedRobot();
        GyroRegulatedDifferentialDrive drive = drive(robot);
        drive.setObstacleDetector(blockedAfter(Integer.MAX_VALUE));

        // When
        boolean completed = drive.travel(600);

        // Then: 250 + 250 + 100 mm, the wheels' degrees all run
        assertThat(completed).isTrue();
        assertThat(drive.lastOutcome()).isEqualTo(MoveOutcome.COMPLETED);
        assertThat(drive.travelledMillimetres()).isEqualTo(600);
        assertThat(robot.leftTacho).isEqualTo(349 + 349 + 140);
    }
}
