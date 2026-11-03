package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.HubPort;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.util.Delay;

/**
 * Checks that the encoders of two motors on ports A and B report their position. It connects to the hub, subscribes
 * both ports to {@link RemotePoweredUpHub#MODE_MOTOR_POSITION}, and prints the latest position in degrees and the
 * number of reports received for each port every half second, after the hub's battery level. This copy targets the
 * M5Stack Cardputer, whose core uses the NimBLE Bluetooth stack.
 */
@Board({M5StackCardputerBoard.class})
public class M5CardputerMotorEncoders {
    private static final RemotePoweredUpHub HUB = new RemotePoweredUpHub();
    private static final int PRINT_INTERVAL_MS = 500;
    private static final int DRIVE_POWER_PERCENT = 60;
    private static final int DRIVE_MILLIS = 1500;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("M5CardputerMotorEncoders started");
        while (true) {
            if (!HUB.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                if (!HUB.connect(10000)) {
                    Serial.println("No connection; retrying");
                    Delay.millis(1000);
                    continue;
                }
                HUB.enableSensor(HubPort.A, RemotePoweredUpHub.MODE_MOTOR_POSITION);
                HUB.enableSensor(HubPort.B, RemotePoweredUpHub.MODE_MOTOR_POSITION);
                Serial.println("Connected; encoders enabled on A and B");
                Serial.print("battery ");
                Serial.print(HUB.batteryPercent());
                Serial.println(" %");
            }
            driveMotors(HubPort.A);
            driveMotors(HubPort.B);
            printEncoders();
            Delay.millis(PRINT_INTERVAL_MS);
        }
    }

    /** Runs one motor forward for a moment, then brakes it, so its encoder shows the movement. */
    private static void driveMotors(HubPort port) {
        HUB.getMotor(port).drive(DRIVE_POWER_PERCENT);
        Delay.millis(DRIVE_MILLIS);
        HUB.getMotor(port).stop();
        Delay.millis(DRIVE_MILLIS);
    }

    private static void printEncoders() {
        if (!HUB.isConnected()) {
            Serial.println("Hub disconnected");
            return;
        }
        Serial.print("A degrees=");
        Serial.print(HUB.readSensor(HubPort.A));
        Serial.print(" reports=");
        Serial.print(HUB.sensorReportCount(HubPort.A));
        Serial.print(" | B degrees=");
        Serial.print(HUB.readSensor(HubPort.B));
        Serial.print(" reports=");
        Serial.print(HUB.sensorReportCount(HubPort.B));
        Serial.print(" | battery=");
        Serial.println(HUB.batteryPercent());
    }
}
