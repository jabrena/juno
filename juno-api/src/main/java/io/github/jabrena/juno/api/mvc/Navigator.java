package io.github.jabrena.juno.api.mvc;

/**
 * What a {@link View} uses to hand over control. Views are named by the {@code int} ids the program registered them
 * under, so a view needs no reference to the others. A request takes effect between frames, never half-way through
 * the view's own touch handler.
 */
public interface Navigator {
    /** Makes the view registered as {@code viewId} the active one; the current view is remembered for {@link #back}. */
    void show(int viewId);

    /** Returns to the view that was active before the current one; does nothing when there is none. */
    void back();
}
