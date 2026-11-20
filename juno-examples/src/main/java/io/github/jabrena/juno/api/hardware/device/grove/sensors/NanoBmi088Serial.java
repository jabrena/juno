package io.github.jabrena.juno.api.hardware.device.grove.sensors;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.imu.Bmi088;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Reads a Grove BMI088 IMU plugged into a {@link NanoConnectorCarrier}'s Grove I2C socket and prints heading, yaw
 * rate and acceleration.
 *
 * <p>Use the carrier's Grove I2C socket (or its Qwiic connector, the same bus), not an analog socket, and set the
 * carrier's input selector switch to 3.3 V for the Nano ESP32. Keep the IMU still during startup calibration.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.device.grove.sensors.NanoBmi088Serial -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoBmi088Serial {
    private static final int PRINT_MILLIS = 200;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);

        Serial.println("BMI088: initializing");
        Delay.millis(500);
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(MCU.getGpio());
        Bmi088 imu = new Bmi088(carrier.getI2c(), MCU.getClock());
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
