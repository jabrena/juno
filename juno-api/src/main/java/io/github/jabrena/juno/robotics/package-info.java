/**
 * Board-independent motor contracts. {@link io.github.jabrena.juno.robotics.DCMotor} provides
 * open-loop power control, {@link io.github.jabrena.juno.robotics.Encoder} provides position
 * feedback, and {@link io.github.jabrena.juno.robotics.RegulatedMotor} adds encoder-backed
 * speed and position control. Concrete adapters belong under the relevant board or device package
 * and must preserve these primitive, allocation-free contracts.
 */
@NullMarked
package io.github.jabrena.juno.robotics;

import org.jspecify.annotations.NullMarked;
