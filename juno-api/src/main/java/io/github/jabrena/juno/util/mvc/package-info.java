/**
 * A small model-view-controller toolkit for multi-screen programs on a TFT touch shield.
 *
 * <p>Each screen is a {@link io.github.jabrena.juno.util.mvc.View}; it reads the program's own model, and asks its
 * {@link io.github.jabrena.juno.util.mvc.Navigator} to show another view or go back, by the {@code int} id that view
 * was registered under, so views never reference each other. A {@link io.github.jabrena.juno.util.mvc.ViewRouter}
 * owns the views, runs the frame loop on the calling thread, dispatches new touches, applies navigation between
 * frames and keeps a short back stack; an optional {@link io.github.jabrena.juno.util.mvc.Task} is advanced a step
 * every frame for background work without a thread.
 *
 * <p>Unlike the rest of the API these are ordinary objects, but they follow the same zero-allocation rule: the router
 * and the views are built once at startup, and the frame loop allocates nothing.
 */
@NullMarked
package io.github.jabrena.juno.util.mvc;

import org.jspecify.annotations.NullMarked;
