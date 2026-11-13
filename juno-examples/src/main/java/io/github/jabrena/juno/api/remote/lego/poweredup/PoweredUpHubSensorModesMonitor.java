package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.robotics.Accelerometer;
import io.github.jabrena.juno.robotics.Gyroscope;
import io.github.jabrena.juno.robotics.SampleProvider;
import io.github.jabrena.juno.util.Delay;

/**
 * Prints the Technic Hub's (88012) built-in motion sensors twice a second over Serial: the acceleration in whole metres
 * per second squared, the gyro's heading and yaw rate, and the tilt angles. The gyro and tilt values are printed times
 * 100, so a fraction survives the whole-number print. Tilt the hub, spin it and watch which value changes; that shows the
 * axes, the units and the range on your hub before a program relies on them, which the library documents but has not
 * verified. Pick the board with {@code -Djuno.board}.
 */
public class PoweredUpHubSensorModesMonitor {
    private static final int REPORT_MILLIS = 500;
    private static final int SCALE = 100;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RemotePoweredUpHub hub = new RemotePoweredUpHub(MCU.getBluetoothLe(), MCU.getClock());
        Accelerometer accelerometer = hub.getAccelerometer();
        Gyroscope gyroscope = hub.getGyroscope();
        SampleProvider tilt = hub.getTilt();
        float[] sample = new float[3];

        while (true) {
            if (!hub.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                hub.connect(0);
                Serial.println("Battery: " + hub.batteryPercent() + " %");
            }
            Serial.println("Acceleration m/s^2: " + accelerometer.getXAccel() + ", " + accelerometer.getYAccel()
                    + ", " + accelerometer.getZAccel());
            Serial.println("Heading x100: " + gyroscope.getAngle() * SCALE
                    + ", yaw rate x100: " + (int) (gyroscope.getAngularVelocity() * SCALE));
            tilt.fetchSample(sample, 0);
            Serial.println("Tilt x100: " + (int) (sample[0] * SCALE) + ", " + (int) (sample[1] * SCALE) + ", "
                    + (int) (sample[2] * SCALE));
            Delay.millis(REPORT_MILLIS);
        }
    }
}
