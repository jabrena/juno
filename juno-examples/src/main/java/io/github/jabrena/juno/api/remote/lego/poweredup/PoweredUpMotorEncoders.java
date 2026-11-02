package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpHubRemote;
import io.github.jabrena.juno.util.Delay;

/**
 * Checks that the encoders of two motors on ports A and B report their position. It connects to the hub, subscribes
 * both ports to {@link PoweredUpHubRemote#MODE_MOTOR_POSITION}, and prints the latest position in degrees and the
 * number of reports received for each port every half second. Turn the motors by hand to see the values change.
 */
@Board({ArduinoNanoEsp32Board.class})
public class PoweredUpMotorEncoders {
    private static final PoweredUpHubRemote HUB = new PoweredUpHubRemote();
    private static final int PRINT_INTERVAL_MS = 500;
    private static final int DRIVE_POWER_PERCENT = 60;
    private static final int DRIVE_MILLIS = 1500;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("PoweredUpMotorEncoders started");
        while (true) {
            if (!HUB.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                if (!HUB.connect(10000)) {
                    Serial.println("No connection; retrying");
                    Delay.millis(1000);
                    continue;
                }
                HUB.enableSensor(PoweredUpHubRemote.PORT_A, PoweredUpHubRemote.MODE_MOTOR_POSITION);
                HUB.enableSensor(PoweredUpHubRemote.PORT_B, PoweredUpHubRemote.MODE_MOTOR_POSITION);
                Serial.println("Connected; encoders enabled on A and B");
            }
            driveMotors(PoweredUpHubRemote.PORT_A);
            driveMotors(PoweredUpHubRemote.PORT_B);
            printEncoders();
            Delay.millis(PRINT_INTERVAL_MS);
        }
    }

    /** Runs one motor forward for a moment, then brakes it, so its encoder shows the movement. */
    private static void driveMotors(int port) {
        HUB.setMotorPower(port, DRIVE_POWER_PERCENT);
        Delay.millis(DRIVE_MILLIS);
        HUB.brakeMotor(port);
        Delay.millis(DRIVE_MILLIS);
    }

    private static void printEncoders() {
        if (!HUB.isConnected()) {
            Serial.println("Hub disconnected");
            return;
        }
        Serial.print("A degrees=");
        Serial.print(HUB.readSensor(PoweredUpHubRemote.PORT_A));
        Serial.print(" reports=");
        Serial.print(HUB.sensorReportCount(PoweredUpHubRemote.PORT_A));
        Serial.print(" | B degrees=");
        Serial.print(HUB.readSensor(PoweredUpHubRemote.PORT_B));
        Serial.print(" reports=");
        Serial.println(HUB.sensorReportCount(PoweredUpHubRemote.PORT_B));
    }
}
