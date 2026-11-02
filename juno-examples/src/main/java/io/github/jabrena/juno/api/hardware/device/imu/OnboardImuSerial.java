package io.github.jabrena.juno.api.hardware.device.imu;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.imu.Imu;
import io.github.jabrena.juno.hardware.imu.ImuSample;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Prints the onboard inertial sensor of the Arduino UNO WiFi Rev2 (its LSM6DS3TR) over USB serial, several times a
 * second. Each line carries the heading (degrees, clockwise from where the board pointed at startup), the yaw rate
 * (degrees per second, clockwise positive), and the roll and pitch from gravity (degrees), for example:
 *
 * <pre>{@code
 * heading=87.4 yaw=-0.2 roll=1.1 pitch=-2.0
 * }</pre>
 *
 * <p>Keep the board still while it starts: about 2 seconds go to measuring the gyroscope's bias, and the heading
 * drifts from there on. Turn the board and watch the heading follow; a turn to the right increases it. Roll and pitch
 * are only meaningful while the board is not accelerating.
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
    private static final int MILLIDEGREES_PER_DEGREE = 1000;

    private OnboardImuSerial() {
    }

    /**
     * Starts the sensor, then prints one orientation line every {@link #PRINT_MILLIS} milliseconds indefinitely.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);

        Imu imu = ArduinoUno.getImu();
        ImuSample sample = new ImuSample();
        Serial.println("Calibrating: keep the board still");
        if (!imu.begin()) {
            Serial.println("LSM6DS3TR not found");
            while (true) {
                Delay.millis(1000);
            }
        }
        Serial.println("LSM6DS3TR ready");

        while (true) {
            if (imu.read(sample)) {
                Serial.print("heading=");
                printDegrees(sample.headingMillidegrees());
                Serial.print(" yaw=");
                printDegrees(sample.yawRateMillidegreesPerSecond());
                Serial.print(" roll=");
                printDegrees(sample.rollMillidegrees());
                Serial.print(" pitch=");
                printDegrees(sample.pitchMillidegrees());
                Serial.println("");
            } else {
                Serial.println("read failed");
            }
            Delay.millis(PRINT_MILLIS);
        }
    }

    /** Prints a milli-degree value as degrees with one decimal place, sign first. */
    private static void printDegrees(int millidegrees) {
        int tenths = Math.abs(millidegrees) / (MILLIDEGREES_PER_DEGREE / 10);
        if (millidegrees < 0) {
            Serial.print("-");
        }
        Serial.print(tenths / 10);
        Serial.print(".");
        Serial.print(tenths % 10);
    }
}
