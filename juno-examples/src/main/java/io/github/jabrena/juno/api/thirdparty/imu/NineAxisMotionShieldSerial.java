package io.github.jabrena.juno.api.thirdparty.imu;

import io.github.jabrena.juno.api.hardware.io.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.io.serial.Serial;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Prints the orientation of the Arduino 9 Axis Motion Shield over USB serial, several times a second.
 * Each line carries the heading (degrees, clockwise from the power-up direction), the pitch (degrees,
 * {@code 0} when the shield lies flat), the gyroscope rate about X (sixteenths of a degree per second)
 * and the packed calibration byte (2 bits per sensor; {@code 255} means all fully calibrated), for
 * example:
 *
 * <pre>{@code
 * heading=87 pitch=-2 gyroX=0 cal=255
 * }</pre>
 *
 * <p>Wiring: stack the shield on the board so its SDA/SCL pins are the board's I2C pins. Nothing
 * else is needed; the BNO055 is read over I2C with no Arduino library. It tries address {@code 0x28}
 * and then {@code 0x29}, and prints which one answered. If neither answers, the program prints a
 * message and stops.
 *
 * <p>After uploading the sketch, open a serial monitor at the same baud rate:
 * <pre>{@code
 * arduino-cli monitor -p <PORT> -c baudrate=115200
 * }</pre>
 *
 * <p>Pick the board with {@code -Djuno.board}.
 */
public final class NineAxisMotionShieldSerial {
    private final NineAxisMotionShield imu = new NineAxisMotionShield();
    private static final int PRINT_MILLIS = 200;
    private static final int UNITS_PER_DEGREE = 16;

    private NineAxisMotionShieldSerial() {
    }

    /**
     * Starts the shield, then prints one orientation line every {@link #PRINT_MILLIS} milliseconds
     * indefinitely.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        new NineAxisMotionShieldSerial().run();
    }

    private void run() {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);

        if (imu.begin()) {
            Serial.println("BNO055 ready at 0x28");
        } else if (imu.begin(imu.getAddressAlternate())) {
            Serial.println("BNO055 ready at 0x29");
        } else {
            Serial.println("BNO055 not found at 0x28 or 0x29");
            while (true) {
                Delay.millis(1000);
            }
        }

        while (true) {
            Serial.print("heading=");
            Serial.print(imu.headingDegrees());
            Serial.print(" pitch=");
            Serial.print(imu.pitchRaw() / UNITS_PER_DEGREE);
            Serial.print(" gyroX=");
            Serial.print(imu.gyroXRaw());
            Serial.print(" cal=");
            Serial.println(imu.calibration());
            Delay.millis(PRINT_MILLIS);
        }
    }
}
