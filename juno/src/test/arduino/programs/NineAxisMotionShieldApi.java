package demo;

import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.imu.NineAxisMotionShield;

/** The BNO055 orientation sensor over I2C. */
public final class NineAxisMotionShieldApi {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        NineAxisMotionShield.begin();

        Serial.println(NineAxisMotionShield.headingDegrees());
        Serial.println(NineAxisMotionShield.pitchRaw());
        Serial.println(NineAxisMotionShield.gyroXRaw());
        Serial.println(NineAxisMotionShield.calibration());
    }
}
