/**
 * USB serial output: {@link io.github.jabrena.juno.hardware.serial.Serial} and its {@link
 * io.github.jabrena.juno.hardware.serial.BaudRate} constants.
 *
 * <p>{@code Serial} is the primary way a running Juno program is observable at all, since the
 * board has no attached debugger or console. {@code print}/{@code println} accept an {@code int}
 * or a compile-time string literal only — Juno has no heap for a runtime-built {@code String} —
 * so computed values are printed as separate literal/int calls rather than one formatted string.
 */
@NullMarked
package io.github.jabrena.juno.hardware.serial;

import org.jspecify.annotations.NullMarked;
