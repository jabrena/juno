package io.github.jabrena.juno.robotics.navigation;

/**
 * Moves a robot straight and turns it in place, as leJOS's {@code MoveController} with its rotation does: the moves a
 * {@link Navigator} drives a path with. {@link #travel} and {@link #rotate} block until the move ends and report whether
 * it completed; a failure, a timeout or a {@link #stop()} returns {@code false} rather than throwing, since the
 * navigator runs them on a thread of its own.
 *
 * <p>Two kinds implement it: {@link GyroRegulatedDifferentialDrive}, whose wheels are regulated elsewhere (an RCX) and
 * whose heading is corrected between moves, and {@link GyroDifferentialPilot}, which drives two encoder motors in a loop
 * of its own and steers with the heading while it moves.
 */
public interface Pilot {

    /**
     * Drives straight by {@code millimetres}, forward when positive, and waits until it ends. Returns {@code true} when
     * the travel completed, {@code false} when it failed, timed out or was stopped; {@link #travelledMillimetres()}
     * then says how far it got.
     */
    boolean travel(int millimetres);

    /**
     * Turns in place by {@code degrees}, counter-clockwise when positive, and waits until it ends. Returns {@code true}
     * when the turn completed, {@code false} when it failed, timed out or was stopped.
     */
    boolean rotate(int degrees);

    /** Asks the move in progress to end as soon as it can; safe to call from another thread. */
    void stop();

    /** The distance the last {@link #travel} covered, in millimetres, signed as it was asked, also when it ended early. */
    int travelledMillimetres();

    /** Why the last {@link #travel} or {@link #rotate} ended. */
    MoveOutcome lastOutcome();
}
