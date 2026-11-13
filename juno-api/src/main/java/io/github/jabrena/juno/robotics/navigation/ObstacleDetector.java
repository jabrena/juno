package io.github.jabrena.juno.robotics.navigation;

/**
 * Says whether something is in the robot's way, read by a {@link Pilot} on its own thread while it travels: leJOS's
 * {@code FeatureDetector} without the thread of its own, which a board's few thread slots cannot spare.
 * {@link RangeObstacleDetector} reads a {@link io.github.jabrena.juno.robotics.RangeFinder}.
 */
public interface ObstacleDetector {

    /** Takes a reading and returns whether the way is blocked now. */
    boolean blocked();

    /** The distance to the obstacle that last made {@link #blocked()} true, in millimetres. */
    int rangeMillimetres();

    /** Forgets what earlier readings saw, as at the start of a move. */
    void reset();
}
