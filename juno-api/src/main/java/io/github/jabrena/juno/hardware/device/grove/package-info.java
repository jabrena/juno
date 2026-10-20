/**
 * Zero-allocation drivers and pin names for peripherals connected through the Grove Base Shield.
 *
 * <p>The shield is passive: its analog, digital, I2C and UART sockets are the UNO header pins
 * exposed by {@link io.github.jabrena.juno.hardware.device.grove.GroveBaseShield}. A driver such as
 * {@link io.github.jabrena.juno.hardware.device.grove.sensors.GroveDht22} is therefore ordinary Java built from the
 * low-level GPIO and timing intrinsics.
 */
@NullMarked
package io.github.jabrena.juno.hardware.device.grove;

import org.jspecify.annotations.NullMarked;
