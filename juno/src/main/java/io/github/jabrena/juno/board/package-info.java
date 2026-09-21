/**
 * Juno's model of a compilation target: {@link io.github.jabrena.juno.board.Board}, resolved from
 * the entry-point class's {@code @Board} annotation (see
 * {@link io.github.jabrena.juno.annotations.Board}) via
 * {@link io.github.jabrena.juno.board.Board#fromApiClassName}, or
 * {@link io.github.jabrena.juno.board.Board#DEFAULT} when the entry point carries none.
 *
 * <p>Each constant carries the data the rest of the compiler and {@code juno-maven-plugin} need
 * for that board: its Arduino CLI FQBN, a display name, and which optional peripherals it exposes
 * (currently the LED matrix and Wi-Fi), so board-specific intrinsics can be gated without every
 * caller re-deriving that from the annotation type.
 */
package io.github.jabrena.juno.board;
