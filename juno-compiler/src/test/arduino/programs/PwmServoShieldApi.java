package demo;

import io.github.jabrena.juno.api.thirdparty.servo.PwmServoShield;

/** Servos on a PCA9685 shield over I2C. */
public final class PwmServoShieldApi {
    public static void main(String[] args) {
        PwmServoShield.begin();

        PwmServoShield.write(0, 90);
        PwmServoShield.writeMicros(1, 1500);
        PwmServoShield.release(0);
    }
}
