/**
 * Board keyboards. {@link io.github.jabrena.juno.hardware.keyboard.Keyboard} is polled by the board program: call its
 * {@code update()} method in the main loop, then read the current keys when {@code isChanged()} reports a transition.
 */
@NullMarked
package io.github.jabrena.juno.hardware.keyboard;

import org.jspecify.annotations.NullMarked;
