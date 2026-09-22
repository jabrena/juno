/**
 * Drivers for character LCD shields, currently
 * {@link io.github.jabrena.juno.api.lcd.LcdKeypadShield}: a 16x2 HD44780-compatible display in
 * 4-bit mode plus 5 buttons read from a single resistor-ladder analog pin, wired for the shield's
 * standard pinout.
 *
 * <p>Unlike {@code led}'s {@code LedMatrix}, a shield here needs no new compiler intrinsic —
 * every operation is built entirely from {@link io.github.jabrena.juno.api.io.Gpio} pin
 * operations and {@link io.github.jabrena.juno.api.Delay}.
 */
package io.github.jabrena.juno.api.lcd;
