/**
 * The board's own hardware and program-wide introspection: {@link io.github.jabrena.juno.api.hardware.MCU} hands out
 * the board's pins and clock, {@link io.github.jabrena.juno.api.hardware.Memory} reports Juno's arena
 * and the program's flash usage, and {@link io.github.jabrena.juno.api.hardware.BoardRuntime} tells which board
 * the program was compiled for.
 */
@NullMarked
package io.github.jabrena.juno.api.hardware;

import org.jspecify.annotations.NullMarked;
