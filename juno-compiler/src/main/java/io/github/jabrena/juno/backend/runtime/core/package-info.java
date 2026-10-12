/**
 * One {@link io.github.jabrena.juno.backend.runtime.core.CoreRuntime} per {@link io.github.jabrena.juno.board.ArduinoCore}:
 * every board-to-core difference lives in exactly one implementation here. The implementations are package-private,
 * so the rest of the compiler can only reach them through the sealed interface.
 */
@NullMarked
package io.github.jabrena.juno.backend.runtime.core;

import org.jspecify.annotations.NullMarked;
