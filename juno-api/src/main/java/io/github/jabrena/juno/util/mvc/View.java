package io.github.jabrena.juno.util.mvc;

/**
 * One screen of a multi-view program. A view renders the model it was given and turns touches into actions; to hand
 * control to another view it asks its {@link Navigator}, and never holds a reference to another view.
 */
public interface View {
    /** The display was cleared for this view: draw the parts that never change and forget what was drawn before. */
    void enter();

    /** Called on every frame while this view is active: redraw only what changed in the model. */
    void render();

    /** A new touch at ({@code x}, {@code y}) while this view is active. */
    void touch(int x, int y);
}
