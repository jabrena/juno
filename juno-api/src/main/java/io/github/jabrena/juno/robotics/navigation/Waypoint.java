package io.github.jabrena.juno.robotics.navigation;

/**
 * A point a {@link Navigator} drives to, in the pose's Cartesian millimetres, optionally with the heading the robot must
 * face there, as leJOS's {@code Waypoint}. Unlike leJOS's, the position tolerance is enforced: the navigator drives
 * another leg while the robot ends a leg further than {@link #maxPositionErrorMillimetres()} from the point. Create the
 * path's waypoints once at startup.
 */
public final class Waypoint {
    /** The tolerance that leaves it to the navigator's default. */
    public static final int NAVIGATOR_DEFAULT = -1;

    private int xMillimetres;
    private int yMillimetres;
    private final int headingDegrees;
    private final boolean headingRequired;
    private int maxPositionErrorMillimetres = NAVIGATOR_DEFAULT;

    /** A point to reach, facing whichever way the robot arrives. */
    public Waypoint(int xMillimetres, int yMillimetres) {
        this(xMillimetres, yMillimetres, 0, false);
    }

    /** A point to reach and then face {@code headingDegrees}, counter-clockwise from +x. */
    public Waypoint(int xMillimetres, int yMillimetres, int headingDegrees) {
        this(xMillimetres, yMillimetres, headingDegrees, true);
    }

    private Waypoint(int xMillimetres, int yMillimetres, int headingDegrees, boolean headingRequired) {
        this.xMillimetres = xMillimetres;
        this.yMillimetres = yMillimetres;
        this.headingDegrees = headingDegrees;
        this.headingRequired = headingRequired;
    }

    /** Moves the point: a planner reuses the detour waypoints it allocated at startup instead of allocating new ones. */
    void moveTo(int xMillimetres, int yMillimetres) {
        this.xMillimetres = xMillimetres;
        this.yMillimetres = yMillimetres;
    }

    public int xMillimetres() {
        return xMillimetres;
    }

    public int yMillimetres() {
        return yMillimetres;
    }

    /** The heading to face on arrival, in degrees counter-clockwise from +x; meaningful when it is required. */
    public int headingDegrees() {
        return headingDegrees;
    }

    public boolean isHeadingRequired() {
        return headingRequired;
    }

    /** How close the robot must end to the point, in millimetres, or {@link #NAVIGATOR_DEFAULT}. */
    public int maxPositionErrorMillimetres() {
        return maxPositionErrorMillimetres;
    }

    /**
     * Sets how close the robot must end to the point, in millimetres, or {@link #NAVIGATOR_DEFAULT}.
     *
     * @throws IllegalArgumentException if it is neither positive nor {@link #NAVIGATOR_DEFAULT}
     */
    public void setMaxPositionErrorMillimetres(int millimetres) {
        if (millimetres <= 0 && millimetres != NAVIGATOR_DEFAULT) {
            throw new IllegalArgumentException("the position error must be positive");
        }
        maxPositionErrorMillimetres = millimetres;
    }
}
