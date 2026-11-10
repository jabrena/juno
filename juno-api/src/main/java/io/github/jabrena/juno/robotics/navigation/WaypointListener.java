package io.github.jabrena.juno.robotics.navigation;

/**
 * Receives a path one waypoint at a time from whatever plans it, as leJOS's interface of the same name does: a
 * {@link Navigator} implements it, and a planner such as {@link DetourPlanner} feeds it. The planner sends each
 * waypoint with {@link #addWaypoint} and then {@link #pathGenerated()} once the path is whole.
 */
public interface WaypointListener {

    /** Appends {@code waypoint} to the path being received. */
    void addWaypoint(Waypoint waypoint);

    /** The path is complete. A navigator starts, or resumes, following it. */
    void pathGenerated();
}
