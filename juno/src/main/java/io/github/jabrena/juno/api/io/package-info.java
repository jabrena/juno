/**
 * General-purpose digital/analog I/O: {@link io.github.jabrena.juno.api.io.Gpio}, the raw
 * pin-number operations ({@code pinMode}/{@code digitalWrite}/{@code digitalRead}/{@code
 * analogRead}/{@code analogWrite}/{@code toggle}), and {@link
 * io.github.jabrena.juno.api.io.DigitalOutput}, a zero-cost handle bound to one output pin
 * ({@code high}/{@code low}/{@code toggle}/{@code isHigh}) for callers that prefer an
 * object-shaped API over passing a pin number to every call.
 *
 * <p>This package's subpackages group I/O operations that need more than raw pins: {@code usb}
 * (Serial), {@code hid} (USB mouse control), and {@code net} (Wi-Fi/HTTP/JSON).
 */
package io.github.jabrena.juno.api.io;
