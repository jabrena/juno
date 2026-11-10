package io.github.jabrena.juno.robotics.navigation;

/**
 * Hears a {@link Navigator}'s progress along its path, as leJOS's listener of the same name does. The calls come from the
 * navigator's own thread, so keep them short; the pose is the navigator's, read it there and then.
 */
public interface NavigationListener {

    /** The robot reached {@code waypoint}, the {@code sequence}-th reached since the navigator was created. */
    void atWaypoint(Waypoint waypoint, Pose pose, int sequence);

    /** {@code waypoint} was the path's last: it is now empty. Called after {@link #atWaypoint} for the same waypoint. */
    void pathComplete(Waypoint waypoint, Pose pose, int sequence);

    /**
     * Driving to {@code waypoint} ended without reaching it: {@link Navigator#stop()} was called, or a move failed or
     * timed out, or the robot stayed outside the waypoint's tolerance. The waypoint stays first in the path.
     */
    void pathInterrupted(Waypoint waypoint, Pose pose, int sequence);
}
