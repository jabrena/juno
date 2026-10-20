/**
 * The board's own hardware and program-wide introspection: {@link io.github.jabrena.juno.api.hardware.MCU} hands out
 * the board's pins, I2C bus, USB mouse, LED matrix, clock, {@link io.github.jabrena.juno.api.hardware.BoardModel} (which
 * board the program was compiled for) and {@link io.github.jabrena.juno.api.hardware.BoardMemory} (Juno's arena and
 * the program's flash usage). Board I/O has one subpackage per interface: {@code gpio} holds the pin-level
 * digital/analog operations, {@code i2c} the I2C register access and {@code serial} Serial.
 */
@NullMarked
package io.github.jabrena.juno.api.hardware;

import org.jspecify.annotations.NullMarked;
