package io.github.jabrena.juno.api.hardware.device.imu;

import io.github.jabrena.juno.hardware.device.imu.NineAxisMotionShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Prints the orientation of the Arduino 9 Axis Motion Shield over USB serial, several times a second. Each line carries
 * the heading (whole degrees, clockwise from the power-up direction), the yaw rate (whole degrees per second,
 * clockwise positive) and the acceleration along X, Y and Z (whole metres per second squared), for example:
 *
 * <pre>{@code
 * heading=87 yaw=0 ax=0 ay=0 az=9
 * }</pre>
 *
 * <p>Wiring: stack the shield on the board so its SDA/SCL pins are the board's I2C pins. Nothing
 * else is needed; the BNO055 is read over I2C with no Arduino library. It answers at address {@code 0x28}.
 * If it does not answer, the program prints a message and stops.
 *
 * <p>After uploading the sketch, open a serial monitor at the same baud rate:
 * <pre>{@code
 * arduino-cli monitor -p <PORT> -c baudrate=115200
 * }</pre>
 *
 * <p>Pick the board with {@code -Djuno.board}.
 */
public final class NineAxisMotionShieldSerial {
    private static final int PRINT_MILLIS = 200;

    private NineAxisMotionShieldSerial() {
    }

    /**
     * Starts the shield, then prints one orientation line every {@link #PRINT_MILLIS} milliseconds
     * indefinitely.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);

        NineAxisMotionShield imu = new NineAxisMotionShield();
        if (!imu.begin()) {
            Serial.println("BNO055 not found at 0x28");
            while (true) {
                Delay.millis(1000);
            }
        }
        Serial.println("BNO055 ready at 0x28");
        imu.reset();

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
