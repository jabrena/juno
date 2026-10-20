package demo;

import io.github.jabrena.juno.api.hardware.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.serial.Serial;
import io.github.jabrena.juno.api.thirdparty.imu.NineAxisMotionShield;

/** The BNO055 orientation sensor over I2C. */
public final class NineAxisMotionShieldApi {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        NineAxisMotionShield imu = new NineAxisMotionShield();
        imu.begin();

        Serial.println(imu.headingDegrees());
        Serial.println(imu.pitchRaw());
        Serial.println(imu.gyroXRaw());
        Serial.println(imu.calibration());
    }
}
