/**
 * Board-independent motion coordination. {@link io.github.jabrena.juno.robotics.navigation.DifferentialDrive}
 * combines two {@link io.github.jabrena.juno.robotics.DCMotor} instances, while
 * {@link io.github.jabrena.juno.robotics.navigation.DifferentialDriveOdometry} combines encoders and an
 * inertial sensor and {@link io.github.jabrena.juno.robotics.navigation.HeadingHoldController} applies
 * heading corrections without depending on a motor protocol or device implementation.
 */
@org.jspecify.annotations.NullMarked
package io.github.jabrena.juno.robotics.navigation;
