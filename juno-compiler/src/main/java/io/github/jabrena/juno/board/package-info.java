/**
 * Juno's model of a compilation target: {@link io.github.jabrena.juno.board.Board}, resolved from
 * the entry-point class's {@code @Board} annotation (see
 * {@link io.github.jabrena.juno.annotations.Board}) via
 * {@link io.github.jabrena.juno.board.Board#fromApiClassName}, or
 * {@link io.github.jabrena.juno.board.Board#DEFAULT} when the entry point carries none.
 *
 * <p>Each constant carries the data the rest of the compiler and {@code juno-maven-plugin} need
 * for that board: its Arduino CLI FQBN, a display name, the {@link io.github.jabrena.juno.board.ArduinoCore}
 * it is built with, and the set of optional {@link io.github.jabrena.juno.board.Capability capabilities}
 * it provides (LED matrix, Wi-Fi, watchdog). The linker gates capability-dependent intrinsics on the
 * latter; the backend picks its per-core runtime from the former. Neither re-derives board behavior
 * from the annotation type or from per-board flags.
 */
@NullMarked
package io.github.jabrena.juno.board;

import org.jspecify.annotations.NullMarked;
