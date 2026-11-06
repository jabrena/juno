/**
 * Board-independent motor and sensor contracts. {@link io.github.jabrena.juno.robotics.DCMotor} provides
 * open-loop power control, {@link io.github.jabrena.juno.robotics.Encoder} provides position
 * feedback, {@link io.github.jabrena.juno.robotics.EncoderMotor} combines the two, and
 * {@link io.github.jabrena.juno.robotics.RegulatedMotor} adds encoder-backed
 * speed and position control.
 *
 * <p>Sensor contracts: {@link io.github.jabrena.juno.robotics.SampleProvider} fetches samples
 * into a caller-owned {@code float[]}, {@link io.github.jabrena.juno.robotics.SensorMode} names one way of reading a
 * sensor and {@link io.github.jabrena.juno.robotics.SensorModes} switches between several. The single-purpose
 * {@link io.github.jabrena.juno.robotics.LightDetector}, {@link io.github.jabrena.juno.robotics.Gyroscope},
 * {@link io.github.jabrena.juno.robotics.Accelerometer} and {@link io.github.jabrena.juno.robotics.RangeFinder}
 * cover common sensors.
 * Concrete adapters belong under the relevant board or device package
 * and must preserve these primitive, allocation-free contracts.
 */
@NullMarked
package io.github.jabrena.juno.robotics;

import org.jspecify.annotations.NullMarked;
