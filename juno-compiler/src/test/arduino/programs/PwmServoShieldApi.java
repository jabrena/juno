package demo;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.servo.PwmServoShield;

/** Servos on a PCA9685 shield over I2C. */
public final class PwmServoShieldApi {
    public static void main(String[] args) {
        PwmServoShield shield = new PwmServoShield(ArduinoUno.getI2c(), PwmServoShield.ADDRESS_DEFAULT);

        shield.write(0, 90);
        shield.writeMicros(1, 1500);
        shield.release(0);
    }
}
