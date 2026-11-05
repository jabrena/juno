package demo;

import io.github.jabrena.juno.hardware.device.imu.NineAxisMotionShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** The BNO055 orientation sensor over I2C. */
public final class NineAxisMotionShieldApi {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        NineAxisMotionShield imu = new NineAxisMotionShield();
        imu.begin();
        imu.reset();

        Serial.println(imu.getAngle());
        Serial.println(imu.getXAccel());
        Serial.println((int) imu.getAngularVelocity());
    }
}
