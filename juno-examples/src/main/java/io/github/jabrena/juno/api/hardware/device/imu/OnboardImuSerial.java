package io.github.jabrena.juno.api.hardware.device.imu;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.robotics.Accelerometer;
import io.github.jabrena.juno.robotics.Gyroscope;
import io.github.jabrena.juno.util.Delay;

/**
 * Prints the onboard accelerometer and gyroscope of the Arduino UNO WiFi Rev2 (its LSM6DS3TR) over USB serial, several
 * times a second. Each line carries the heading (whole degrees, clockwise from where the board pointed at startup), the
 * yaw rate (whole degrees per second, clockwise positive), and the acceleration along X, Y and Z (whole metres per
 * second squared), for example:
 *
 * <pre>{@code
 * heading=87 yaw=0 ax=0 ay=0 az=9
 * }</pre>
 *
 * <p>Keep the board still while it starts: about 2 seconds go to measuring the gyroscope's bias, and the heading
 * drifts from there on. Turn the board and watch the heading follow; a turn to the right increases it. The
 * accelerations are whole metres per second squared, so small movements read as 0.
 *
 * <p>After uploading the sketch, open a serial monitor at the same baud rate:
 * <pre>{@code
 * arduino-cli monitor -p <PORT> -c baudrate=115200
 * }</pre>
 *
 * <p>Pick the board with {@code -Djuno.board=arduino-uno-r3-wifi-rev2}.
 */
public final class OnboardImuSerial {
    private static final int PRINT_MILLIS = 200;

    private OnboardImuSerial() {
    }

    /**
     * Starts the sensor, then prints one line every {@link #PRINT_MILLIS} milliseconds indefinitely.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);

        Serial.println("Calibrating: keep the board still");
        Gyroscope gyroscope = MCU.getGyroscope();
        Accelerometer accelerometer = MCU.getAccelerometer();
        Serial.println("LSM6DS3TR ready");

        while (true) {
            Serial.print("heading=");
            Serial.print(gyroscope.getAngle());
            Serial.print(" yaw=");
            Serial.print((int) gyroscope.getAngularVelocity());
            Serial.print(" ax=");
            Serial.print(accelerometer.getXAccel());
            Serial.print(" ay=");
            Serial.print(accelerometer.getYAccel());
            Serial.print(" az=");
            Serial.println(accelerometer.getZAccel());
            Delay.millis(PRINT_MILLIS);
        }
    }
}
