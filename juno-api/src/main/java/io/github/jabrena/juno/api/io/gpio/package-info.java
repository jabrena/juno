/**
 * General-purpose digital/analog I/O: {@link io.github.jabrena.juno.api.io.gpio.Gpio}, the raw
 * pin-number operations ({@code pinMode}/{@code digitalWrite}/{@code digitalRead}/{@code
 * analogRead}/{@code analogWrite}/{@code toggle}), and {@link
 * io.github.jabrena.juno.api.io.gpio.DigitalOutput}, a zero-cost handle bound to one output pin
 * ({@code high}/{@code low}/{@code toggle}/{@code isHigh}) for callers that prefer an
 * object-shaped API over passing a pin number to every call.
 *
 * <p>{@link io.github.jabrena.juno.api.io.gpio.PortNoise} collects an explicitly non-cryptographic startup seed
 * by sampling a user-provided physical noise circuit on two pins.
 */
@NullMarked
package io.github.jabrena.juno.api.io.gpio;

import org.jspecify.annotations.NullMarked;
