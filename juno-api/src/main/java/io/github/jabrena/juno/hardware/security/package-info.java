/**
 * Secrets kept off the SD card and out of the firmware: {@link io.github.jabrena.juno.hardware.security.SecretStore}
 * reads named values, and {@link io.github.jabrena.juno.hardware.security.SecretStoreFactory} selects the board's
 * store — the Portenta H7's SE050C2 or an ESP32 board's NVS. Other boards have none.
 */
@NullMarked
package io.github.jabrena.juno.hardware.security;

import org.jspecify.annotations.NullMarked;
