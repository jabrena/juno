package io.github.jabrena.juno.robotics.navigation;

/**
 * Plans a mission's path for a {@link Navigator} and drives it around obstacles found on the way: the planner leJOS's
 * {@code PathFinder} feeds a navigator through its {@link WaypointListener}, re-planning when the navigator says a leg
 * ended at an obstacle, which leJOS leaves to the program.
 *
 * <p>The planner owns the mission, the waypoints to visit in order, and sends the navigator what is left of it. It
 * listens to the navigator, so it is the navigator's {@link NavigationListener}; give it the program's own listener with
 * {@link #setNavigationListener}, and it passes every call on. When a leg ends with {@link MoveOutcome#OBSTACLE}, it
 * sidesteps from where the robot stopped. With the heading's forward direction <b>u</b> = (cos θ, sin θ), its left
 * <b>n</b> = (−sin θ, cos θ), the robot at <b>P</b> and the obstacle {@code r} ahead:
 *
 * <ul>
 *   <li>the side is the one the next mission waypoint <b>T</b> lies on, the sign of the cross product
 *       <b>u</b> × (<b>T</b> − <b>P</b>), left on a tie;</li>
 *   <li>the first detour point is the side step <b>D1</b> = <b>P</b> + side · s · <b>n</b>, with s the robot's half
 *       width, the margin and half the obstacle's assumed width;</li>
 *   <li>the second passes the obstacle, <b>D2</b> = <b>D1</b> + (r + depth + margin) · <b>u</b>;</li>
 *   <li>then the rest of the mission, which the navigator reaches from wherever <b>D2</b> left the robot, since it plans
 *       each leg from the measured pose.</li>
 * </ul>
 *
 * <p>A range finder measures distance, not width, so the obstacle's size is a guess: when the robot is blocked again on
 * the way to the same mission waypoint, the next detour takes the other side, and the side step widens by one step every
 * two detours: once, once, twice, twice. After {@link #setMaxDetours the most detours} for one waypoint, the planner gives up and passes the
 * interruption on. The robot turns in place toward the side step, so the pilot's detector must stop it far enough from
 * the obstacle to turn.
 *
 * <p>The navigator's path must hold the mission and the two detour points: an array two longer than the mission. The
 * detour points are allocated here once; planning allocates nothing. The planner's calls come from the navigator's
 * thread.
 */
public final class DetourPlanner implements NavigationListener {
    /** Detours tried for one mission waypoint before the planner gives up on it. */
    public static final int DEFAULT_MAX_DETOURS = 3;
    private static final int DETOUR_POINTS = 2;
    private static final double RADIANS_PER_MILLIDEGREE = Math.PI / 180_000.0;

    private final Navigator navigator;
    private final ObstacleDetector detector;
    private final Waypoint[] mission;
    private final Waypoint sideStep = new Waypoint(0, 0);
    private final Waypoint pass = new Waypoint(0, 0);
    private NavigationListener listener = new SilentListener();
    private int halfWidthMillimetres = 80;
    private int marginMillimetres = 50;
    private int obstacleWidthMillimetres = 200;
    private int obstacleDepthMillimetres = 200;
    private int maxDetours = DEFAULT_MAX_DETOURS;
    /** The mission waypoint the robot is on its way to. */
    private int next;
    /** Detours tried on the way to it. */
    private int attempts;
    private int detours;
    private int obstacleX;
    private int obstacleY;

    /**
     * A planner for {@code mission}, driven by {@code navigator}, whose pilot reads {@code detector}; it becomes the
     * navigator's listener.
     *
     * @throws IllegalArgumentException if the mission is empty, or the navigator's path cannot hold it and two detour
     *         points
     */
    public DetourPlanner(Navigator navigator, ObstacleDetector detector, Waypoint[] mission) {
        if (mission.length == 0) {
            throw new IllegalArgumentException("the mission needs a waypoint");
        }
        if (navigator.capacity() < mission.length + DETOUR_POINTS) {
            throw new IllegalArgumentException("the navigator's path needs room for the mission and two detour points");
        }
        this.navigator = navigator;
        this.detector = detector;
        this.mission = mission;
        navigator.setNavigationListener(this);
    }

    /** Passes each of the navigator's calls on to {@code listener}, the program's. */
    public void setNavigationListener(NavigationListener listener) {
        this.listener = listener;
    }

    /** The robot's half width, and the clearance kept from the obstacle on every side, in millimetres. */
    public void setRobotHalfWidth(int halfWidthMillimetres, int marginMillimetres) {
        requirePositive(halfWidthMillimetres);
        requirePositive(marginMillimetres);
        this.halfWidthMillimetres = halfWidthMillimetres;
        this.marginMillimetres = marginMillimetres;
    }

    /** The obstacle's assumed width across the robot's way and depth along it, in millimetres. */
    public void setObstacleSize(int widthMillimetres, int depthMillimetres) {
        requirePositive(widthMillimetres);
        requirePositive(depthMillimetres);
        obstacleWidthMillimetres = widthMillimetres;
        obstacleDepthMillimetres = depthMillimetres;
    }

    /** Detours tried for one mission waypoint before the planner gives up on it. */
    public void setMaxDetours(int maxDetours) {
        requirePositive(maxDetours);
        this.maxDetours = maxDetours;
    }

    /**
     * Sends the whole mission to the navigator and starts it, as leJOS's {@code startPathFinding} sends a path to its
     * listeners; the navigator follows it on its own thread.
     */
    public void startPathFinding() {
        next = 0;
        attempts = 0;
        sendRemaining();
    }

    /** How many detours the planner has planned. */
    public int detours() {
        return detours;
    }

    /** Where the last obstacle was, in the navigator's millimetres. */
    public int obstacleXMillimetres() {
        return obstacleX;
    }

    public int obstacleYMillimetres() {
        return obstacleY;
    }

    /** Whether {@code waypoint} is one of the planner's detour points rather than a mission waypoint. */
    public boolean isDetour(Waypoint waypoint) {
        return waypoint == sideStep || waypoint == pass;
    }

    @Override
    public void atWaypoint(Waypoint waypoint, Pose pose, int sequence) {
        if (next < mission.length && waypoint == mission[next]) {
            next++;
            attempts = 0;
        }
        listener.atWaypoint(waypoint, pose, sequence);
    }

    @Override
    public void pathComplete(Waypoint waypoint, Pose pose, int sequence) {
        listener.pathComplete(waypoint, pose, sequence);
    }

    /** Plans a detour when an obstacle ended the leg and detours are left; otherwise passes the interruption on. */
    @Override
    public void pathInterrupted(Waypoint waypoint, Pose pose, int sequence) {
        if (navigator.lastOutcome() != MoveOutcome.OBSTACLE || next >= mission.length || attempts >= maxDetours) {
            listener.pathInterrupted(waypoint, pose, sequence);
            return;
        }
        planDetour(pose, mission[next]);
        attempts++;
        detours++;
        navigator.clearPath();
        navigator.addWaypoint(sideStep);
        navigator.addWaypoint(pass);
        addRemaining();
        navigator.pathGenerated();
    }

    /** Places the two detour points around the obstacle ahead of {@code pose}, toward {@code target}. */
    private void planDetour(Pose pose, Waypoint target) {
        double heading = pose.headingMillidegrees() * RADIANS_PER_MILLIDEGREE;
        double forwardX = Math.cos(heading);
        double forwardY = Math.sin(heading);
        // Left of the heading: the forward direction turned a quarter counter-clockwise.
        double leftX = -forwardY;
        double leftY = forwardX;
        int x = pose.xMillimetres();
        int y = pose.yMillimetres();
        int range = detector.rangeMillimetres() >= 0 ? detector.rangeMillimetres() : marginMillimetres;
        obstacleX = x + (int) Math.round(range * forwardX);
        obstacleY = y + (int) Math.round(range * forwardY);

        // Positive when the target is to the left of the way ahead.
        double cross = forwardX * (target.yMillimetres() - y) - forwardY * (target.xMillimetres() - x);
        int preferred = cross < 0 ? -1 : 1;
        int side = attempts % 2 == 0 ? preferred : -preferred;
        int widen = 1 + attempts / 2;
        double step = side * widen * (halfWidthMillimetres + marginMillimetres + obstacleWidthMillimetres / 2.0);
        double beyond = range + obstacleDepthMillimetres + marginMillimetres;

        int sideX = x + (int) Math.round(step * leftX);
        int sideY = y + (int) Math.round(step * leftY);
        sideStep.moveTo(sideX, sideY);
        pass.moveTo(sideX + (int) Math.round(beyond * forwardX), sideY + (int) Math.round(beyond * forwardY));
    }

    /** Replaces the navigator's path with the mission's remaining waypoints, and starts it. */
    private void sendRemaining() {
        navigator.clearPath();
        addRemaining();
        navigator.pathGenerated();
    }

    private void addRemaining() {
        for (int i = next; i < mission.length; i++) {
            navigator.addWaypoint(mission[i]);
        }
    }

    private static void requirePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("the value must be positive");
        }
    }

    /** The program's listener until one is set. */
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
