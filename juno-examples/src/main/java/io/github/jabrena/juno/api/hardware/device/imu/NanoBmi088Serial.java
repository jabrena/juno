package io.github.jabrena.juno.api.hardware.device.imu;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.imu.Bmi088;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Reads the Grove BMI088 IMU on the Nano ESP32's I2C bus and prints heading, yaw rate and acceleration.
 *
 * <p>Connect the IMU to the carrier's I2C socket, not the A0/A1 socket. Keep it still during startup calibration.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoBmi088Serial {
    private static final int PRINT_MILLIS = 200;

    private NanoBmi088Serial() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);

        Serial.println("BMI088: initializing");
        Delay.millis(500);
        Bmi088 imu = new Bmi088(MCU.getI2c(), MCU.getClock());
        imu.recalibrateOffset();
        Serial.println("BMI088: connected");

        while (true) {
            Serial.print("heading=");
            Serial.print(imu.getAngle());
            Serial.print(" yaw=");
            Serial.print((int) imu.getAngularVelocity());
            Serial.print(" ax=");
            Serial.print(imu.getXAccel());
            Serial.print(" ay=");
            Serial.print(imu.getYAccel());
            Serial.print(" az=");
            Serial.println(imu.getZAccel());
            Delay.millis(PRINT_MILLIS);
        }
    }
}
