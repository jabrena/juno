package io.github.jabrena.juno.games.gladiator.gladiator;

/** An {@link Aggressive} gladiator's round of the arena: the waypoint it heads for and until when it looks round. */
public final class Patrol {
    /** No waypoint chosen. */
    public static final int NONE = -1;

    private int waypoint = NONE;
    private int lookUntil;

    /** Starts the round afresh at {@code now}. */
    public void reset(int now) {
        waypoint = NONE;
        lookUntil = now;
    }

    public int waypoint() {
        return waypoint;
    }

    public int lookUntil() {
        return lookUntil;
    }

    /** Heads for {@code next}, or {@link #NONE} to pick the nearest again. */
    public void headFor(int next) {
        waypoint = next;
    }

    /** Looks round until {@code until}. */
    public void lookUntil(int until) {
        lookUntil = until;
    }
}
