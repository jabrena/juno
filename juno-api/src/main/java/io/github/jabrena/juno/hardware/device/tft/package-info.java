/**
 * Drivers for TFT display shields, currently
 * {@link io.github.jabrena.juno.hardware.device.tft.TftTouchShield}: the ELEGOO 2.8" 240x320 ILI9341 display
 * on an 8-bit parallel bus, with a resistive touch panel and a microSD socket (its card is
 * {@link io.github.jabrena.juno.hardware.device.tft.TftTouchShield#getSdCard()}).
 *
 * <p>Like {@code lcd}'s {@code LcdKeypadShield}, a shield here needs no new compiler intrinsic —
 * every operation is built entirely from {@link io.github.jabrena.juno.hardware.gpio.Gpio} pin operations
 * and {@link io.github.jabrena.juno.util.Delay}.
 */
@NullMarked
package io.github.jabrena.juno.hardware.device.tft;

import org.jspecify.annotations.NullMarked;
