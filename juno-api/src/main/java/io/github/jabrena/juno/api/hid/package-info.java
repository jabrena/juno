/**
 * USB HID mouse control: {@link io.github.jabrena.juno.api.hid.Mouse}, backed by the Arduino
 * {@code Mouse} library.
 *
 * <p>Requires a board with native USB (the UNO R4 WiFi) and the {@code Mouse} library installed.
 * Once a program calls {@code Mouse.begin()}, it takes control of the pointer on whatever
 * computer the board's USB port is plugged into, so uploading such a sketch needs a known way to
 * recover (disconnect the board, or use its reset/bootloader sequence).
 */
@NullMarked
package io.github.jabrena.juno.api.hid;

import org.jspecify.annotations.NullMarked;
