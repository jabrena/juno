package io.github.jabrena.juno.api;

/**
 * Read-only introspection into Juno's arena allocator, recognized as a compiler intrinsic. Useful
 * for a health-check heartbeat over {@link io.github.jabrena.juno.api.io.usb.Serial}: a value
 * that keeps climbing and never comes back down across many collection cycles indicates something
 * is holding references it shouldn't (an unintended leak), whereas one that rises and falls as
 * requests are served and reclaimed is healthy.
 */
public final class Memory {
    private Memory() {
    }

    /** Bytes currently allocated in Juno's arena (never negative, capped at the arena's fixed capacity). */
    public static native int arenaUsedBytes();
}
