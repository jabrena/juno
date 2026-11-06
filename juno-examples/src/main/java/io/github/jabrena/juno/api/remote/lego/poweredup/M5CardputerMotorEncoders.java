package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.M5StackCardputer;
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
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int PRINT_INTERVAL_MS = 500;
    private static final int DRIVE_POWER_PERCENT = 60;
    private static final int DRIVE_MILLIS = 1500;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("M5CardputerMotorEncoders started");
        RemotePoweredUpHub hub = new RemotePoweredUpHub(M5StackCardputer.getBluetoothLe());
        while (true) {
            if (!hub.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                if (!hub.connect(CONNECT_TIMEOUT_MILLIS)) {
                    Serial.println("No connection; retrying");
                    Delay.millis(1000);
                    continue;
                }
                hub.enableSensor(HubPort.A, RemotePoweredUpHub.MODE_MOTOR_POSITION);
                hub.enableSensor(HubPort.B, RemotePoweredUpHub.MODE_MOTOR_POSITION);
                Serial.println("Connected; encoders enabled on A and B");
                Serial.print("battery ");
                Serial.print(hub.batteryPercent());
                Serial.println(" %");
            }
            hub.getMotor(HubPort.A).drive(DRIVE_POWER_PERCENT);
            Delay.millis(DRIVE_MILLIS);
            hub.getMotor(HubPort.A).stop();
            Delay.millis(DRIVE_MILLIS);
            hub.getMotor(HubPort.B).drive(DRIVE_POWER_PERCENT);
            Delay.millis(DRIVE_MILLIS);
            hub.getMotor(HubPort.B).stop();
            Delay.millis(DRIVE_MILLIS);
            if (!hub.isConnected()) {
                Serial.println("Hub disconnected");
            } else {
                Serial.print("A degrees=");
                Serial.print(hub.readSensor(HubPort.A));
                Serial.print(" reports=");
                Serial.print(hub.sensorReportCount(HubPort.A));
                Serial.print(" | B degrees=");
                Serial.print(hub.readSensor(HubPort.B));
                Serial.print(" reports=");
                Serial.print(hub.sensorReportCount(HubPort.B));
                Serial.print(" | battery=");
                Serial.println(hub.batteryPercent());
            }
            Delay.millis(PRINT_INTERVAL_MS);
        }
    }
}
