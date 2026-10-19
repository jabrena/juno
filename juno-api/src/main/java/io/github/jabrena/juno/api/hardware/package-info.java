/**
 * The board's own hardware and program-wide introspection: {@link io.github.jabrena.juno.api.hardware.MCU} hands out
 * the board's pins, I2C bus, USB mouse, LED matrix, clock, {@link io.github.jabrena.juno.api.hardware.BoardModel} (which
 * board the program was compiled for) and {@link io.github.jabrena.juno.api.hardware.BoardMemory} (Juno's arena and
 * the program's flash usage).
 */
@NullMarked
package io.github.jabrena.juno.api.hardware;

import org.jspecify.annotations.NullMarked;
