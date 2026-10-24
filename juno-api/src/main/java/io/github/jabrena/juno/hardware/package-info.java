/**
 * The board's own hardware and program-wide introspection: {@link io.github.jabrena.juno.hardware.ArduinoUno} and
 * {@link io.github.jabrena.juno.hardware.ArduinoNano} hand out
 * the board's pins, I2C bus, USB mouse, LED matrix, clock, {@link io.github.jabrena.juno.hardware.BoardModel} (which
 * board the program was compiled for) and {@link io.github.jabrena.juno.hardware.BoardMemory} (Juno's arena and
 * the program's flash usage). Board I/O has one subpackage per interface: {@code gpio} holds the pin-level
 * digital/analog operations, {@code i2c} the I2C register access, {@code serial} Serial, {@code imu} the
 * device-independent inertial sensing contracts, {@code motor} the board-independent motor contracts,
 * {@code drive} two-motor drive coordination, and {@code net} the Wi-Fi
 * networking stack and {@code time} the {@link io.github.jabrena.juno.hardware.time.Clock} interface.
 */
@NullMarked
package io.github.jabrena.juno.hardware;

import org.jspecify.annotations.NullMarked;
