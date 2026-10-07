/**
 * General-purpose digital/analog I/O: {@link io.github.jabrena.juno.api.io.Gpio}, the raw
 * pin-number operations ({@code pinMode}/{@code digitalWrite}/{@code digitalRead}/{@code
 * analogRead}/{@code analogWrite}/{@code toggle}), and {@link
 * io.github.jabrena.juno.api.io.DigitalOutput}, a zero-cost handle bound to one output pin
 * ({@code high}/{@code low}/{@code toggle}/{@code isHigh}) for callers that prefer an
 * object-shaped API over passing a pin number to every call.
 *
 * <p>This package's subpackages group I/O operations that need more than raw pins: {@code usb}
 * (Serial) and {@code storage} (SPI SD cards and properties files). Networking and USB HID live in
 * the sibling {@code io.github.jabrena.juno.api.net} and {@code io.github.jabrena.juno.api.hid}
 * packages.
 */
package io.github.jabrena.juno.api.io;
