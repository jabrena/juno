package io.github.jabrena.juno.robotics;

/**
 * A motor that runs forward or backward until it is stopped. Implementations are primitive and allocation-free, so a
 * motor handle can be used in the board program.
 */
public interface BaseMotor {

    /** Turns the motor forward until {@link #stop()} or {@link #flt()} is called. */
    void forward();

    /** Turns the motor in reverse until {@link #stop()} or {@link #flt()} is called. */
    void backward();

    /** Halts the motor at once and resists further movement, unlike {@link #flt()}, which lets it coast. */
    void stop();

    /** Removes all power so the motor coasts to a stop, unlike {@link #stop()}, which holds it in place. */
    void flt();

    /** Returns {@code true} while the motor is commanded to move, otherwise {@code false}. */
    boolean isMoving();
}
