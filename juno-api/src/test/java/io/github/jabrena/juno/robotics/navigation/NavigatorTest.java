package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.DirectionFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Navigator: waypoints with a gyroscope's heading")
class NavigatorTest {

    /**
     * A robot on a plane with a pilot that turns {@code turnScale} of what it is asked and travels {@code travelScale};
     * the direction finder reads its true heading, as a gyroscope would, and the encoders report the distance driven.
     */
    private static final class SimulatedRobot implements Pilot, DirectionFinder {
        double x;
        double y;
        double headingDegrees;
        double turnScale = 1.0;
        double travelScale = 1.0;
        boolean failTravel;
        int travelled;
        /** The heading a travel ends with beyond the one it drove along: a pilot that weaves stops mid-swing. */
        double stopSwingDegrees;
        /** Whether the pilot measures the sideways offset; this robot drives straight, so it measures none. */
        boolean measuresLeft;
        final List<Integer> rotations = new ArrayList<>();
        final List<Integer> travels = new ArrayList<>();

        @Override
        public boolean travel(int millimetres) {
            travels.add(millimetres);
            if (failTravel) {
                travelled = 0;
                return false;
            }
            double driven = millimetres * travelScale;
            double radians = Math.toRadians(headingDegrees);
            x += driven * Math.cos(radians);
            y += driven * Math.sin(radians);
            travelled = (int) Math.round(driven);
            headingDegrees += stopSwingDegrees;
            return true;
        }

        @Override
        public boolean rotate(int degrees) {
            rotations.add(degrees);
            headingDegrees += degrees * turnScale;
            return true;
        }

        @Override
        public void stop() {
        }

        @Override
        public int travelledMillimetres() {
            return travelled;
        }

        @Override
        public int travelledLeftMillimetres() {
            return measuresLeft ? 0 : Pilot.UNMEASURED;
        }

        @Override
        public MoveOutcome lastOutcome() {
            return failTravel ? MoveOutcome.FAILED : MoveOutcome.COMPLETED;
        }

        @Override
        public float getDegreesCartesian() {
            return (float) headingDegrees;
        }

        @Override
        public void resetCartesianZero() {
            headingDegrees = 0;
        }

        @Override
        public void startCalibration() {
        }

        @Override
        public void stopCalibration() {
        }
    }

    private static final class Recorder implements NavigationListener {
        final List<String> events = new ArrayList<>();

        @Override
        public void atWaypoint(Waypoint waypoint, Pose pose, int sequence) {
            events.add("at " + sequence);
        }

        @Override
        public void pathComplete(Waypoint waypoint, Pose pose, int sequence) {
            events.add("complete " + sequence);
        }

        @Override
        public void pathInterrupted(Waypoint waypoint, Pose pose, int sequence) {
            events.add("interrupted " + sequence);
        }
    }

    @Test
    @DisplayName("A square is driven turn by turn and each waypoint is reported")
    void shouldDriveASquareAndReportEachWaypoint() {
        // Given
        SimulatedRobot robot = new SimulatedRobot();
        Navigator navigator = new Navigator(robot, robot, new Waypoint[4]);
        Recorder recorder = new Recorder();
        navigator.setNavigationListener(recorder);
        navigator.setPose(0, 0, 0);
        navigator.addWaypoint(new Waypoint(500, 0));
        navigator.addWaypoint(new Waypoint(500, -500));
        navigator.addWaypoint(new Waypoint(0, -500));
        navigator.addWaypoint(new Waypoint(0, 0, 0));

        // When
        drive(navigator);

        // Then: right turns are clockwise, negative; the last waypoint's heading turns the robot back to zero
        assertThat(robot.rotations).containsExactly(-90, -90, -90, -90);
        assertThat(robot.travels).containsExactly(500, 500, 500, 500);
        assertThat(recorder.events).containsExactly("at 1", "at 2", "at 3", "at 4", "complete 4");
        assertThat(navigator.pathCompleted()).isTrue();
        assertThat(robot.x).isCloseTo(0, within(1.0));
        assertThat(robot.y).isCloseTo(0, within(1.0));
    }

    @Test
    @DisplayName("A pilot's measured line offset places the robot, not the heading it stopped mid-swing with")
    void shouldPlaceTheRobotByTheMeasuredOffsetWhenTheTravelStopsMidSwing() {
        // Given: a 1 m leg driven straight, whose heading reads 13 degrees off at the stop, as a weaving pilot's does
        SimulatedRobot robot = new SimulatedRobot();
        robot.stopSwingDegrees = -13;
        robot.measuresLeft = true;
        Navigator navigator = new Navigator(robot, robot, new Waypoint[1]);
        navigator.setPose(0, 0, 0);
        navigator.addWaypoint(new Waypoint(1000, 0));

        // When
        drive(navigator);

        // Then: one leg reaches the point; the pose is on the line, not 113 mm to the right of it
        assertThat(robot.travels).containsExactly(1000);
        assertThat(navigator.pathCompleted()).isTrue();
        assertThat(navigator.getPose().yMillimetres()).isCloseTo(0, within(1));
    }

    @Test
    @DisplayName("Without a measured offset, a heading caught mid-swing places the robot off its line")
    void shouldPlaceTheRobotOffItsLineWithoutAMeasuredOffset() {
        // Given: the same leg, from a pilot that does not measure the sideways offset
        SimulatedRobot robot = new SimulatedRobot();
        robot.stopSwingDegrees = -13;
        Navigator navigator = new Navigator(robot, robot, new Waypoint[1]);
        navigator.setPose(0, 0, 0);
        navigator.addWaypoint(new Waypoint(1000, 0));

        // When
        drive(navigator);

        // Then: the halfway heading, -6.5 degrees, puts the robot about 113 mm right of the line, so it drives more legs
        assertThat(robot.travels.size()).isGreaterThan(1);
    }

    @Test
    @DisplayName("A turn that comes out short is seen by the gyroscope and corrected by another leg")
    void shouldCorrectAShortTurnWithAnotherLeg() {
        // Given: every turn comes out 10% short
        SimulatedRobot robot = new SimulatedRobot();
        robot.turnScale = 0.9;
        Navigator navigator = new Navigator(robot, robot, new Waypoint[1]);
        navigator.addWaypoint(new Waypoint(0, 500));

        // When
        drive(navigator);

        // Then: the first leg misses by about 78 mm; the second, planned from the measured pose, lands within 20 mm
        assertThat(robot.travels).hasSizeGreaterThanOrEqualTo(2);
        assertThat(Math.hypot(robot.x, robot.y - 500)).isLessThanOrEqualTo(Navigator.DEFAULT_POSITION_ERROR_MILLIMETRES);
    }

    @Test
    @DisplayName("The final heading is reached the shorter way round")
    void shouldTurnToTheFinalHeadingTheShorterWay() {
        // Given: facing 10 degrees, a waypoint here asks for 350
        SimulatedRobot robot = new SimulatedRobot();
        robot.headingDegrees = 10;
        Navigator navigator = new Navigator(robot, robot, new Waypoint[1]);
        navigator.setPose(0, 0, 10);
        navigator.addWaypoint(new Waypoint(0, 0, 350));

        // When
        drive(navigator);

        // Then: 20 degrees clockwise, not 340 counter-clockwise
        assertThat(robot.rotations).containsExactly(-20);
    }

    @Test
    @DisplayName("A failed travel interrupts the path and keeps its waypoint")
    void shouldInterruptThePathWhenATravelFails() {
        // Given
        SimulatedRobot robot = new SimulatedRobot();
        robot.failTravel = true;
        Navigator navigator = new Navigator(robot, robot, new Waypoint[1]);
        Recorder recorder = new Recorder();
        navigator.setNavigationListener(recorder);
        Waypoint target = new Waypoint(300, 0);
        navigator.addWaypoint(target);
        navigator.setPose(0, 0, 0);

        // When
        boolean reached = navigator.navigateOnce();

        // Then
        assertThat(reached).isFalse();
        assertThat(recorder.events).containsExactly("interrupted 0");
        assertThat(navigator.getWaypoint()).isSameAs(target);
    }

    @Test
    @DisplayName("A waypoint still out of reach after the last leg interrupts the path")
    void shouldGiveUpAfterTheLastLeg() {
        // Given: every travel covers only half the distance
        SimulatedRobot robot = new SimulatedRobot();
        robot.travelScale = 0.5;
        Navigator navigator = new Navigator(robot, robot, new Waypoint[1]);
        navigator.addWaypoint(new Waypoint(800, 0));
        navigator.setPose(0, 0, 0);

        // When
        boolean reached = navigator.navigateOnce();

        // Then
        assertThat(reached).isFalse();
        assertThat(robot.travels).hasSize(Navigator.MAX_LEGS);
    }

    @Test
    @DisplayName("The navigator follows its path on its own thread")
    void shouldFollowThePathOnItsOwnThread() {
        // Given
        SimulatedRobot robot = new SimulatedRobot();
        Navigator navigator = new Navigator(robot, robot, new Waypoint[2]);
        navigator.addWaypoint(new Waypoint(200, 0));
        navigator.addWaypoint(new Waypoint(200, 200));

        // When
        navigator.followPath();
        boolean completed = navigator.waitForStop();

        // Then
        assertThat(completed).isTrue();
        assertThat(navigator.isMoving()).isFalse();
        assertThat(navigator.getPose().xMillimetres()).isCloseTo(200, within(1));
        assertThat(navigator.getPose().yMillimetres()).isCloseTo(200, within(1));
    }

    @Test
    @DisplayName("A full path refuses another waypoint")
    void shouldRefuseAWaypointPastTheCapacity() {
        // Given
        SimulatedRobot robot = new SimulatedRobot();
        Navigator navigator = new Navigator(robot, robot, new Waypoint[1]);
        navigator.addWaypoint(new Waypoint(1, 0));

        // When & Then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> navigator.addWaypoint(new Waypoint(2, 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Drives the path on the test's thread, one waypoint at a time, as the navigator's thread would. */
    private static void drive(Navigator navigator) {
        while (navigator.navigateOnce()) {
            // Until the path is empty or interrupted.
        }
    }
}
