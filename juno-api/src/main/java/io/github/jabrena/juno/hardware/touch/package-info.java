/**
 * Touch input: {@link io.github.jabrena.juno.hardware.touch.TouchScreen} is the interface a program or UI depends on, with
 * its coordinates in the same frame as the {@link io.github.jabrena.juno.hardware.display.Display} it covers. The
 * {@code TftTouchShield} and the MHS-3.5 display implement it, and {@code ViewRouter} hit-tests against it.
 */
@NullMarked
package io.github.jabrena.juno.hardware.touch;

import org.jspecify.annotations.NullMarked;
