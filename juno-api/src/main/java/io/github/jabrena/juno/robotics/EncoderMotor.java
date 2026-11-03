package io.github.jabrena.juno.robotics;

/**
 * A motor with basic position feedback: open-loop power control from {@link DCMotor} and the position reported by
 * {@link Encoder}. It has no speed or position control.
 */
public interface EncoderMotor extends DCMotor, Encoder {
}
