package io.github.jabrena.juno.api.thirdparty.lego.poweredup;

import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Prints what the Technic Hub's (88012) built-in motion sensors report, twice a second over Serial:
 * the accelerometer, the gyroscope and the tilt sensor, three values each, plus the hub's battery level.
 * Tilt the hub, spin it and watch which value changes; that shows the axes, the units and the
 * range on your hub before a program relies on them, which the library documents but has not verified.
 * Pick the board with {@code -Djuno.board}.
 */
public class PoweredUpHubImuMonitor {
    private static final PoweredUpHubRemote HUB = new PoweredUpHubRemote();
    private static final int REPORT_MILLIS = 500;
    private static final int VALUE_BYTES = 2;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        PoweredUpHubImuMonitor app = new PoweredUpHubImuMonitor();

        while (true) {
            if (!HUB.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                HUB.connect(0);
                HUB.enableSensor(PoweredUpHubRemote.PORT_TECHNIC_ACCELEROMETER, PoweredUpHubRemote.MODE_IMU_VALUES);
                HUB.enableSensor(PoweredUpHubRemote.PORT_TECHNIC_GYRO, PoweredUpHubRemote.MODE_IMU_VALUES);
                HUB.enableSensor(PoweredUpHubRemote.PORT_TECHNIC_TILT, PoweredUpHubRemote.MODE_IMU_VALUES);
                Serial.println("Battery: " + HUB.batteryPercent() + " %");
            }
            app.print("accelerometer", PoweredUpHubRemote.PORT_TECHNIC_ACCELEROMETER);
            app.print("gyro", PoweredUpHubRemote.PORT_TECHNIC_GYRO);
            app.print("tilt", PoweredUpHubRemote.PORT_TECHNIC_TILT);
            Delay.millis(REPORT_MILLIS);
        }
    }

    private void print(String name, int port) {
        Serial.println(name + " (" + HUB.sensorReportSize(port) + " bytes): "
                + HUB.readSensorValue(port, 0, VALUE_BYTES) + ", "
                + HUB.readSensorValue(port, 1, VALUE_BYTES) + ", "
                + HUB.readSensorValue(port, 2, VALUE_BYTES));
    }
}
