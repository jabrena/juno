package demo;

import io.github.jabrena.juno.thirdparty.servo.PwmServoShield;

/** Servos on a PCA9685 shield over I2C. */
public final class PwmServoShieldApi {
    public static void main(String[] args) {
        PwmServoShield shield = new PwmServoShield();
        shield.begin();

        shield.write(0, 90);
        shield.writeMicros(1, 1500);
        shield.release(0);
    }
}
