package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.DirectionFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DetourPlanner: around obstacles through the WaypointListener")
class DetourPlannerTest {
    private static final int STOP_MILLIMETRES = 250;
    private static final int STEP_MILLIMETRES = 5;
    private static final int RANGE_LIMIT_MILLIMETRES = 2000;

    /**
     * A robot in a room of boxes, with exact turns and travels in 5 mm steps: a travel ends when the box ahead is nearer
     * than {@link #STOP_MILLIMETRES}, as a pilot reading its detector would. It is the pilot, the direction finder and
     * the detector, and it records every position it passes, so a test can check it never drove into a box.
     */
    private static final class SimulatedWorld implements Pilot, DirectionFinder, ObstacleDetector {
        final List<int[]> boxes = new ArrayList<>();
        final List<double[]> trail = new ArrayList<>();
        double x;
        double y;
        double headingDegrees;
        boolean failTravel;
        int travelled;
        int range = -1;
        MoveOutcome outcome = MoveOutcome.COMPLETED;

        /** A box from {@code (x0, y0)} to {@code (x1, y1)}. */
        void box(int x0, int y0, int x1, int y1) {
            boxes.add(new int[] {x0, y0, x1, y1});
        }

        boolean inBox(double px, double py) {
            for (int[] box : boxes) {
                if (px >= box[0] && px <= box[2] && py >= box[1] && py <= box[3]) {
                    return true;
                }
            }
            return false;
        }

        /** The distance to the nearest box straight ahead, or -1 within the range limit. */
        int castAhead() {
            double radians = Math.toRadians(headingDegrees);
            for (int d = 0; d <= RANGE_LIMIT_MILLIMETRES; d += STEP_MILLIMETRES) {
                if (inBox(x + d * Math.cos(radians), y + d * Math.sin(radians))) {
                    return d;
                }
            }
            return -1;
        }

        @Override
        public boolean travel(int millimetres) {
            double radians = Math.toRadians(headingDegrees);
            double driven = 0;
            outcome = MoveOutcome.COMPLETED;
            if (failTravel) {
                outcome = MoveOutcome.FAILED;
            }
            while (outcome == MoveOutcome.COMPLETED && driven < millimetres) {
                int ahead = castAhead();
                if (ahead >= 0 && ahead < STOP_MILLIMETRES) {
                    range = ahead;
                    outcome = MoveOutcome.OBSTACLE;
                    break;
                }
                double step = Math.min(STEP_MILLIMETRES, millimetres - driven);
                x += step * Math.cos(radians);
                y += step * Math.sin(radians);
                driven += step;
                trail.add(new double[] {x, y});
            }
            travelled = (int) Math.round(driven);
            return outcome == MoveOutcome.COMPLETED;
        }

        @Override
        public boolean rotate(int degrees) {
            headingDegrees += degrees;
            outcome = MoveOutcome.COMPLETED;
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
            return Pilot.UNMEASURED;
        }

        @Override
        public MoveOutcome lastOutcome() {
            return outcome;
        }

        @Override
        public boolean blocked() {
            int ahead = castAhead();
            return ahead >= 0 && ahead < STOP_MILLIMETRES;
        }

        @Override
        public int rangeMillimetres() {
            return range;
        }

        @Override
        public void reset() {
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
            events.add("at");
        }

        @Override
        public void pathComplete(Waypoint waypoint, Pose pose, int sequence) {
            events.add("complete");
        }

        @Override
        public void pathInterrupted(Waypoint waypoint, Pose pose, int sequence) {
            events.add("interrupted");
        }
    }

    @Test
    @DisplayName("A box on the way is passed on the left and the mission's waypoint is still reached")
    void shouldDriveAroundABoxAndReachTheWaypoint() {
        // Given: a 200 mm box centred 700 mm ahead, on the way to a waypoint 1500 mm ahead
        SimulatedWorld world = new SimulatedWorld();
        world.box(600, -100, 800, 100);
        Waypoint target = new Waypoint(1500, 0);
        Navigator navigator = new Navigator(world, world, new Waypoint[3]);
        DetourPlanner planner = new DetourPlanner(navigator, world, new Waypoint[] {target});
        Recorder recorder = new Recorder();
        planner.setNavigationListener(recorder);

        // When
        planner.startPathFinding();
        boolean completed = navigator.waitForStop();

        // Then: one detour, two detour points and the waypoint reached, and never inside the box
        assertThat(completed).isTrue();
        assertThat(planner.detours()).isEqualTo(1);
        assertThat(planner.obstacleXMillimetres()).isBetween(595, 605);
        assertThat(recorder.events).containsExactly("at", "at", "at", "complete");
        assertThat(Math.hypot(world.x - 1500, world.y)).isLessThanOrEqualTo(Navigator.DEFAULT_POSITION_ERROR_MILLIMETRES);
        assertThat(world.trail).noneMatch(point -> world.inBox(point[0], point[1]));
        // Passed on the left: the side step went to positive y
        assertThat(world.trail.stream().mapToDouble(point -> point[1]).max().orElse(0)).isGreaterThan(150);
    }

    @Test
    @DisplayName("Each detour point is reported as one, the mission's waypoint as not")
    void shouldTellDetourPointsFromMissionWaypoints() {
        // Given
        SimulatedWorld world = new SimulatedWorld();
        world.box(600, -100, 800, 100);
        Waypoint target = new Waypoint(1500, 0);
        Navigator navigator = new Navigator(world, world, new Waypoint[3]);
        DetourPlanner planner = new DetourPlanner(navigator, world, new Waypoint[] {target});
        List<Boolean> detourFlags = new ArrayList<>();
        planner.setNavigationListener(new NavigationListener() {
            @Override
            public void atWaypoint(Waypoint waypoint, Pose pose, int sequence) {
                detourFlags.add(planner.isDetour(waypoint));
            }

            @Override
            public void pathComplete(Waypoint waypoint, Pose pose, int sequence) {
            }

            @Override
            public void pathInterrupted(Waypoint waypoint, Pose pose, int sequence) {
            }
        });

        // When
        planner.startPathFinding();
        navigator.waitForStop();

        // Then
        assertThat(detourFlags).containsExactly(true, true, false);
    }

    @Test
    @DisplayName("A wall with no way round is tried on both sides, then the interruption is passed on")
    void shouldGiveUpAfterTheLastDetour() {
        // Given: a wall across the whole way
        SimulatedWorld world = new SimulatedWorld();
        world.box(600, -5000, 800, 5000);
        Navigator navigator = new Navigator(world, world, new Waypoint[3]);
        DetourPlanner planner = new DetourPlanner(navigator, world, new Waypoint[] {new Waypoint(1500, 0)});
        Recorder recorder = new Recorder();
        planner.setNavigationListener(recorder);

        // When
        planner.startPathFinding();
        boolean completed = navigator.waitForStop();

        // Then
        assertThat(completed).isFalse();
        assertThat(planner.detours()).isEqualTo(DetourPlanner.DEFAULT_MAX_DETOURS);
        assertThat(recorder.events).endsWith("interrupted");
        assertThat(world.x).isLessThan(600);
    }

    @Test
    @DisplayName("An interruption that is not an obstacle is passed on without a detour")
    void shouldPassOnAFailureWithoutADetour() {
        // Given: every travel fails
        SimulatedWorld world = new SimulatedWorld();
        world.failTravel = true;
        Navigator navigator = new Navigator(world, world, new Waypoint[3]);
        DetourPlanner planner = new DetourPlanner(navigator, world, new Waypoint[] {new Waypoint(500, 0)});
        Recorder recorder = new Recorder();
        planner.setNavigationListener(recorder);

        // When
        planner.startPathFinding();
        navigator.waitForStop();

        // Then
        assertThat(planner.detours()).isZero();
        assertThat(recorder.events).containsExactly("interrupted");
    }

    @Test
    @DisplayName("A mission that leaves no room in the navigator's path for the detour points is refused")
    void shouldRefuseANavigatorWithoutRoomForTheDetour() {
        SimulatedWorld world = new SimulatedWorld();
        Navigator navigator = new Navigator(world, world, new Waypoint[2]);

        assertThatThrownBy(() -> new DetourPlanner(navigator, world, new Waypoint[] {new Waypoint(1, 0)}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DetourPlanner(navigator, world, new Waypoint[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
