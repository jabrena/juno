package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpPort;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.util.Delay;

/**
 * Checks that the encoders of two motors on ports A and B report their position. It connects to the hub, subscribes
 * both ports to {@link RemotePoweredUpHub#MODE_MOTOR_POSITION}, and prints the latest position in degrees and the
 * number of reports received for each port every half second. Turn the motors by hand to see the values change.
 */
@Board({ArduinoNanoEsp32Board.class, AiThinkerEsp32CamBoard.class})
public class PoweredUpMotorEncoders {
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int PRINT_INTERVAL_MS = 500;
    private static final int DRIVE_POWER_PERCENT = 60;
    private static final int DRIVE_MILLIS = 1500;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("PoweredUpMotorEncoders started");
        RemotePoweredUpHub hub = new RemotePoweredUpHub(MCU.getBluetoothLe(), MCU.getClock());
        while (true) {
            if (!hub.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                if (!hub.connect(CONNECT_TIMEOUT_MILLIS)) {
                    Serial.println("No connection; retrying");
                    Delay.millis(1000);
                    continue;
                }
                hub.enableSensor(PoweredUpPort.A, RemotePoweredUpHub.MODE_MOTOR_POSITION);
                hub.enableSensor(PoweredUpPort.B, RemotePoweredUpHub.MODE_MOTOR_POSITION);
                Serial.println("Connected; encoders enabled on A and B");
            }
            hub.getMotor(PoweredUpPort.A).drive(DRIVE_POWER_PERCENT);
            Delay.millis(DRIVE_MILLIS);
            hub.getMotor(PoweredUpPort.A).stop();
            Delay.millis(DRIVE_MILLIS);
            hub.getMotor(PoweredUpPort.B).drive(DRIVE_POWER_PERCENT);
            Delay.millis(DRIVE_MILLIS);
            hub.getMotor(PoweredUpPort.B).stop();
            Delay.millis(DRIVE_MILLIS);
            if (!hub.isConnected()) {
                Serial.println("Hub disconnected");
            } else {
                Serial.print("A degrees=");
                Serial.print(hub.readSensor(PoweredUpPort.A));
                Serial.print(" reports=");
                Serial.print(hub.sensorReportCount(PoweredUpPort.A));
                Serial.print(" | B degrees=");
                Serial.print(hub.readSensor(PoweredUpPort.B));
                Serial.print(" reports=");
                Serial.println(hub.sensorReportCount(PoweredUpPort.B));
            }
            Delay.millis(PRINT_INTERVAL_MS);
        }
    }
}
