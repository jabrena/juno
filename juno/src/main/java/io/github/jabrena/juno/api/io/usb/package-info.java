/**
 * USB serial output: {@link io.github.jabrena.juno.api.io.usb.Serial} and its {@link
 * io.github.jabrena.juno.api.io.usb.BaudRate} constants.
 *
 * <p>{@code Serial} is the primary way a running Juno program is observable at all, since the
 * board has no attached debugger or console (see {@code docs/SERIAL.md} in the project
 * documentation). {@code print}/{@code println} accept an {@code int} or a compile-time string
 * literal only — Juno has no heap for a runtime-built {@code String} — so computed values are
 * printed as separate literal/int calls rather than one formatted string.
 */
package io.github.jabrena.juno.api.io.usb;
