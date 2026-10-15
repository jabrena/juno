package io.github.jabrena.juno.usecases.miner.services;

/**
 * Decides whether a node should keep mining or first wait to catch up with a heavier chain that peers have announced.
 * A node that mines while far behind only builds a private branch the network will discard, and it fills the small
 * block pool with blocks nobody wants.
 *
 * <p>The wait is bounded: a peer that keeps announcing more work than anyone can deliver (a stale heartbeat, or one
 * that lies) must not stop this node for good. After {@link #GIVE_UP_MILLIS} without the tip moving, the node mines
 * again until the tip moves, which starts a fresh wait if it is still behind.
 */
public final class SyncGate {
    static final int GIVE_UP_MILLIS = 30_000;

    private static final int IDLE = 0;
    private static final int WAITING = 1;
    private static final int GAVE_UP = 2;

    private int state = IDLE;
    private int since;
    private int seenTipChanges = -1;

    /**
     * Whether to wait instead of mining at time {@code now}: {@code behind} says peers have announced more work than
     * this node's tip holds, and {@code tipChanges} is {@link BlockPool#tipChanges()}.
     */
    public boolean shouldWait(boolean behind, int tipChanges, int now) {
        if (tipChanges != seenTipChanges) {
            seenTipChanges = tipChanges;
            state = IDLE;
        }
        if (!behind) {
            state = IDLE;
            return false;
        }
        if (state == IDLE) {
            state = WAITING;
            since = now;
        }
        if (state == WAITING && now - since >= GIVE_UP_MILLIS) {
            state = GAVE_UP;
        }
        return state == WAITING;
    }
}
