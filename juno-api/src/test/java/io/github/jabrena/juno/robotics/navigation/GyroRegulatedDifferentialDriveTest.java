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

        @Override
        public void rotate(int left, int right, boolean immediateReturn) {
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
}
