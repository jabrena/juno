package io.github.jabrena.juno.api.util.mvc;

/**
 * Background work a {@link ViewRouter} advances a little every frame, before the active view renders: the
 * single-threaded alternative to a background thread, for a board whose RAM has no room for a thread stack.
 */
public interface Task {
    /** Does one short step of the work; does nothing once it is finished. */
    void step();
}
