/**
 * Board-independent motion coordination. {@link io.github.jabrena.juno.hardware.drive.DifferentialDrive}
 * combines two {@link io.github.jabrena.juno.hardware.motor.Motor} instances, while
 * {@link io.github.jabrena.juno.hardware.drive.DifferentialDriveOdometry} combines encoders and an
 * inertial sensor and {@link io.github.jabrena.juno.hardware.drive.HeadingHoldController} applies
 * heading corrections without depending on a motor protocol or device implementation.
 */
@org.jspecify.annotations.NullMarked
package io.github.jabrena.juno.hardware.drive;
