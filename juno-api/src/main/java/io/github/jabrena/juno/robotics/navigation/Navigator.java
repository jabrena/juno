package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.DirectionFinder;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Drives a robot through a path of {@link Waypoint}s, as leJOS's {@code Navigator} does: for each waypoint it turns to
 * face the point, travels to it, and turns to the waypoint's heading when one is required. It runs on a virtual thread
 * of its own, started by the first {@link #followPath()}, so the program can watch, {@link #stop()} it or
 * {@link #waitForStop()}.
 *
 * <p>The pose is the navigator's own, in Cartesian millimetres and degrees (see {@link Pose}). Its heading is always the
 * {@link DirectionFinder}'s, a gyroscope's for instance, so a turn that came out a few degrees short or long, or a
 * travel that drifted, is in the pose as it happened. Its position advances by the distance each travel covered, along
 * the heading the robot had on the way. Each leg is planned from that measured pose, and a robot that ends a leg
 * further from the point than the waypoint's tolerance drives another leg, at most {@link #MAX_LEGS} per waypoint: leJOS
 * plans once and never checks.
 *
 * <p>The pilot does the moves: one that steers with the heading while it travels, such as a {@link GyroDifferentialPilot},
 * or one that corrects it between moves, such as a {@link GyroRegulatedDifferentialDrive}. The path lives in an array
 * the program allocates, {@code new Waypoint[4]} for four waypoints, since a board's arrays have sizes known when the
 * program is compiled; a waypoint is removed once reached. Create the navigator once at startup; driving the path
 * allocates nothing.
 *
 * <p>As a {@link WaypointListener}, it takes its path from a planner, as leJOS's does: the planner sends each waypoint
 * with {@link #addWaypoint} and then {@link #pathGenerated()}, which starts or resumes following it. A planner that
 * listens to the navigator can re-plan from inside {@link NavigationListener#pathInterrupted}: there,
 * {@link #lastOutcome()} says why the leg ended, and {@link #clearPath()}, {@link #addWaypoint} and
 * {@link #pathGenerated()} can be called on the navigator's own thread. {@link DetourPlanner} drives around obstacles
 * that way.
 */
public final class Navigator implements WaypointListener {
    /** How close to a waypoint a leg must end, when the waypoint does not say. */
    public static final int DEFAULT_POSITION_ERROR_MILLIMETRES = 20;
    /** Legs driven toward one waypoint before the navigator gives up on it. */
    public static final int MAX_LEGS = 3;
    /** A turn smaller than this is not made: a pilot's finest turn is about a degree. */
    private static final int MIN_TURN_MILLIDEGREES = 1000;
    private static final int MILLIDEGREES_PER_DEGREE = 1000;
    /** A quarter turn counter-clockwise: the left of a heading. */
    private static final int QUARTER_TURN_MILLIDEGREES = 90_000;
    /** How often the idle thread looks for a path to follow, and {@link #waitForStop()} for the end. */
    private static final int POLL_MILLIS = 20;
    private static final NavigationListener NO_LISTENER = new SilentListener();

    private final Pilot pilot;
    private final DirectionFinder direction;
    private final @Nullable Waypoint[] path;
    private final Pose pose = new Pose();
    private NavigationListener listener = NO_LISTENER;
    private int first;
    private int count;
    private int sequence;
    private int positionErrorMillimetres = DEFAULT_POSITION_ERROR_MILLIMETRES;
    /** What the direction finder reads when the pose's heading is zero, in millidegrees. */
    private int directionAtZero;
    private boolean poseSet;
    private boolean threadStarted;
    private volatile boolean active;
    private volatile boolean stopRequested;
    private volatile boolean singleStep;
    /** Set while the listener hears an interruption, so {@link #waitForStop()} does not end before it re-plans. */
    private volatile boolean notifying;
    private MoveOutcome lastOutcome = MoveOutcome.COMPLETED;

    /**
     * A navigator moving with {@code pilot}, its heading from {@code direction}, that keeps its path in {@code path}: up
     * to that many waypoints. The array becomes the navigator's; its contents are replaced.
     */
    public Navigator(Pilot pilot, DirectionFinder direction, @Nullable Waypoint[] path) {
        if (path.length == 0) {
            throw new IllegalArgumentException("the path needs room for a waypoint");
        }
        this.pilot = pilot;
        this.direction = direction;
        this.path = path;
        for (int i = 0; i < path.length; i++) {
            path[i] = null;
        }
    }

    /** Makes {@code listener} hear each waypoint reached, the path's end and an interruption; one listener at a time. */
    public void setNavigationListener(NavigationListener listener) {
        this.listener = listener;
    }

    /** The tolerance for waypoints that do not set their own, in millimetres. */
    public void setPositionErrorMillimetres(int millimetres) {
        if (millimetres <= 0) {
            throw new IllegalArgumentException("the position error must be positive");
        }
        positionErrorMillimetres = millimetres;
    }

    /**
     * Places the robot at {@code (xMillimetres, yMillimetres)} facing {@code headingDegrees}: the direction finder's
     * current direction becomes that heading. Without it, the first {@link #followPath()} places the robot at the
     * origin facing +x.
     */
    public void setPose(int xMillimetres, int yMillimetres, int headingDegrees) {
        directionAtZero = directionMillidegrees() - headingDegrees * MILLIDEGREES_PER_DEGREE;
        pose.setLocation(xMillimetres, yMillimetres);
        refreshHeading();
        poseSet = true;
    }

    /** The navigator's pose; it changes while the navigator drives. */
    public Pose getPose() {
        refreshHeading();
        return pose;
    }

    /** How many waypoints the path holds at most: the length of the array it was given. */
    public int capacity() {
        return path.length;
    }

    /** How many waypoints the path holds now. */
    public int pathSize() {
        return count;
    }

    /**
     * Why the last leg ended: {@link MoveOutcome#COMPLETED} after a waypoint was reached, the pilot's outcome when a move
     * ended early, {@link MoveOutcome#STOPPED} after {@link #stop()}, or {@link MoveOutcome#FAILED} when the robot stayed
     * outside the waypoint's tolerance after its last leg.
     */
    public MoveOutcome lastOutcome() {
        return lastOutcome;
    }

    /**
     * Appends {@code waypoint} to the path without moving.
     *
     * @throws IllegalStateException if the path is full
     */
    @Override
    public void addWaypoint(Waypoint waypoint) {
        if (count == path.length) {
            throw new IllegalStateException("the path is full");
        }
        path[(first + count) % path.length] = waypoint;
        count++;
    }

    /**
     * Empties the path; a move in progress ends first, as with {@link #stop()}. A listener may call it while it hears an
     * interruption, on the navigator's thread.
     */
    public void clearPath() {
        stop();
        waitWhileActive();
        for (int i = 0; i < path.length; i++) {
            path[i] = null;
        }
        first = 0;
        count = 0;
    }

    /** The planner's path is complete: follows it, as {@link #followPath()} does. */
    @Override
    public void pathGenerated() {
        followPath();
    }

    /** The waypoint being driven to, or the next one, or {@code null} when the path is empty. */
    public @Nullable Waypoint getWaypoint() {
        return count == 0 ? null : path[first];
    }

    /** Appends {@code destination} and follows the path to it. */
    public void goTo(Waypoint destination) {
        addWaypoint(destination);
        followPath();
    }

    /**
     * Starts or resumes driving the path, and returns at once. A path stopped short is resumed toward the waypoint it did
     * not reach, from where the robot is.
     */
    public void followPath() {
        if (!poseSet) {
            setPose(0, 0, 0);
        }
        stopRequested = false;
        active = count > 0;
        if (!threadStarted) {
            threadStarted = true;
            Thread.startVirtualThread(this::run);
        }
    }

    /** With {@code yes}, the navigator stops after each waypoint, until the next {@link #followPath()}. */
    public void singleStep(boolean yes) {
        singleStep = yes;
    }

    /** Ends the move in progress as soon as the pilot can, keeping the path; {@link #followPath()} resumes it. */
    public void stop() {
        stopRequested = true;
        if (active) {
            pilot.stop();
        }
    }

    /**
     * Waits until the navigator stops, and returns whether the path is now empty. A listener that re-plans on an
     * interruption is waited for, so a detour does not count as a stop.
     */
    public boolean waitForStop() {
        while (active || notifying) {
            if (!sleep()) {
                break;
            }
        }
        return pathCompleted();
    }

    private void waitWhileActive() {
        while (active) {
            if (!sleep()) {
                return;
            }
        }
    }

    private static boolean sleep() {
        try {
            Thread.sleep(POLL_MILLIS);
            return true;
        } catch (InterruptedException interrupted) {
            return false;
        }
    }

    /** Whether the navigator is driving its path: it stays {@code true} between the moves of a leg. */
    public boolean isMoving() {
        return active;
    }

    /** Whether the path is empty, every waypoint reached. */
    public boolean pathCompleted() {
        return count == 0;
    }

    private void run() {
        while (true) {
            if (active) {
                navigateOnce();
            } else {
                try {
                    Thread.sleep(POLL_MILLIS);
                } catch (InterruptedException interrupted) {
                    active = false;
                    return;
                }
            }
        }
    }

    /**
     * Drives to the path's first waypoint and tells the listener how it went; the navigator's thread calls it while it is
     * active, and a test can call it directly. Returns whether the waypoint was reached.
     */
    boolean navigateOnce() {
        if (count == 0) {
            active = false;
            return false;
        }
        Waypoint target = Objects.requireNonNull(path[first]);
        if (!driveTo(target)) {
            if (stopRequested) {
                lastOutcome = MoveOutcome.STOPPED;
            }
            notifying = true;
            active = false;
            listener.pathInterrupted(target, pose, sequence);
            notifying = false;
            return false;
        }
        lastOutcome = MoveOutcome.COMPLETED;
        path[first] = null;
        first = (first + 1) % path.length;
        count--;
        sequence++;
        listener.atWaypoint(target, pose, sequence);
        if (count == 0) {
            active = false;
            listener.pathComplete(target, pose, sequence);
        } else if (singleStep) {
            active = false;
        }
        return true;
    }

    /** Legs toward {@code target} until the robot is within its tolerance, then the heading it requires. */
    private boolean driveTo(Waypoint target) {
        int x = target.xMillimetres();
        int y = target.yMillimetres();
        int tolerance = target.maxPositionErrorMillimetres() == Waypoint.NAVIGATOR_DEFAULT
                ? positionErrorMillimetres : target.maxPositionErrorMillimetres();
        int legs = 0;
        while (true) {
            refreshHeading();
            int distance = pose.distanceToMillimetres(x, y);
            if (distance <= tolerance) {
                break;
            }
            if (legs == MAX_LEGS || stopRequested) {
                lastOutcome = MoveOutcome.FAILED;
                return false;
            }
            legs++;
            if (!turnBy(pose.relativeBearingMillidegrees(x, y)) || !travel(distance)) {
                return false;
            }
        }
        if (target.isHeadingRequired()) {
            refreshHeading();
            return turnBy(Pose.shortestTurn(target.headingDegrees() * MILLIDEGREES_PER_DEGREE - pose.headingMillidegrees()));
        }
        return true;
    }

    /** Turns by {@code millidegrees}, counter-clockwise when positive, unless it is too small to make. */
    private boolean turnBy(int millidegrees) {
        if (Math.abs(millidegrees) < MIN_TURN_MILLIDEGREES) {
            return true;
        }
        int degrees = (millidegrees + (millidegrees >= 0 ? 1 : -1) * MILLIDEGREES_PER_DEGREE / 2) / MILLIDEGREES_PER_DEGREE;
        boolean turned = pilot.rotate(degrees);
        refreshHeading();
        lastOutcome = pilot.lastOutcome();
        return turned && !stopRequested;
    }

    /**
     * Travels {@code millimetres} and advances the pose by what the pilot measured: along the heading the leg started on
     * and to its left when the pilot measures the sideways offset; otherwise the distance covered along the heading
     * halfway between the start's and the stop's.
     */
    private boolean travel(int millimetres) {
        int before = pose.headingMillidegrees();
        boolean travelled = pilot.travel(millimetres);
        int after = headingMillidegrees();
        int left = pilot.travelledLeftMillimetres();
        if (left == Pilot.UNMEASURED) {
            pose.advance(pilot.travelledMillimetres(), before + Pose.shortestTurn(after - before) / 2);
        } else {
            // A pilot that steers can weave: the heading at the stop is one moment of the weave, not the leg's direction.
            pose.advance(pilot.travelledMillimetres(), before);
            pose.advance(left, before + QUARTER_TURN_MILLIDEGREES);
        }
        pose.advance(0, after);
        lastOutcome = pilot.lastOutcome();
        return travelled && !stopRequested;
    }

    private void refreshHeading() {
        pose.setHeadingMillidegrees(headingMillidegrees());
    }

    /** The pose's heading now: the direction finder's, from where it read when the pose was placed. */
    private int headingMillidegrees() {
        return directionMillidegrees() - directionAtZero;
    }

    private int directionMillidegrees() {
        return (int) (direction.getDegreesCartesian() * MILLIDEGREES_PER_DEGREE);
    }

    /** The listener until one is set, so a program without one links no listener of its own. */
    private static final class SilentListener implements NavigationListener {
        @Override
        public void atWaypoint(Waypoint waypoint, Pose pose, int sequence) {
        }

        @Override
        public void pathComplete(Waypoint waypoint, Pose pose, int sequence) {
        }

        @Override
        public void pathInterrupted(Waypoint waypoint, Pose pose, int sequence) {
        }
    }
}
