package io.github.jabrena.juno.robotics.navigation;

/** Why a {@link Pilot}'s last move ended, and so why a {@link Navigator}'s leg did. */
public enum MoveOutcome {
    /** The move reached its distance or its turn. */
    COMPLETED,
    /** The pilot's {@link ObstacleDetector} saw something in the way, and the robot stopped short of it. */
    OBSTACLE,
    /** {@link Pilot#stop()} ended it. */
    STOPPED,
    /** It failed or timed out: a stalled wheel, a link that did not answer, a robot that did not move. */
    FAILED
}
