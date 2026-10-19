package io.github.jabrena.juno.api.thirdparty.lego.poweredup;

import io.github.jabrena.juno.api.hardware.MCU;

import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.time.Clock;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Measures how fast a program can talk to a Technic Hub (88012) over Bluetooth LE, to judge whether a
 * control loop such as a self-balancing robot is feasible. It prints three numbers over Serial, then
 * repeats every few seconds:
 * <ul>
 * <li>how many gyroscope reports per second the hub sends (keep the hub still; sensor noise keeps it reporting),</li>
 * <li>how many microseconds one {@code setLinkedMotorPower} command takes to send, and so how many a second can go out,</li>
 * <li>how many milliseconds pass from starting a motor on port A until its rotation sensor shows it moving
 *     (a tacho motor is needed; this includes the motor's own spin-up, so it is an upper bound on the link delay).</li>
 * </ul>
 * Roughly, a balance loop needs 50 reports and commands per second or more, with the motion delay well
 * under 50 ms. Pick the board with {@code -Djuno.board}.
 */
public class PoweredUpHubLatencyProbe {
    private static final PoweredUpHubRemote HUB = new PoweredUpHubRemote();
    private static final Clock CLOCK = MCU.getClock();
    private static final int LEFT = PoweredUpHubRemote.PORT_A;
    private static final int RIGHT = PoweredUpHubRemote.PORT_B;
    private static final int GYRO = PoweredUpHubRemote.PORT_TECHNIC_GYRO;
    private static final int SENSOR_SETTLE_MILLIS = 500;
    private static final int REPORT_WINDOW_MILLIS = 2000;
    private static final int COMMANDS = 100;
    private static final int MOTION_DEGREES = 3;
    private static final int MOTION_TIMEOUT_MILLIS = 1000;
    private static final int MOTOR_POWER = 50;
    private static final int ROUND_PAUSE_SECONDS = 3;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        PoweredUpHubLatencyProbe app = new PoweredUpHubLatencyProbe();

        while (true) {
            if (!HUB.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                HUB.connect(0);
                HUB.enableSensor(GYRO, PoweredUpHubRemote.MODE_IMU_VALUES);
                HUB.enableSensor(LEFT, PoweredUpHubRemote.MODE_MOTOR_POSITION);
                Delay.millis(SENSOR_SETTLE_MILLIS);
            }
            app.measureReports();
            app.measureCommands();
            app.measureMotion();
            Delay.seconds(ROUND_PAUSE_SECONDS);
        }
    }

    private void measureReports() {
        int before = HUB.sensorReportCount(GYRO);
        int started = CLOCK.millis();
        while (CLOCK.millis() - started < REPORT_WINDOW_MILLIS) {
            HUB.readSensor(GYRO);
        }
        int elapsed = CLOCK.millis() - started;
        int reports = HUB.sensorReportCount(GYRO) - before;
        Serial.println("Gyro reports: " + reports * 1000 / elapsed + " per second");
    }

    private void measureCommands() {
        int pair = HUB.linkMotors(LEFT, RIGHT);
        if (pair < 0) {
            Serial.println("Could not pair motors A and B");
            return;
        }
        int started = CLOCK.micros();
        for (int command = 0; command < COMMANDS; command++) {
            HUB.setLinkedMotorPower(pair, 0, 0);
        }
        int perCommand = (CLOCK.micros() - started) / COMMANDS;
        Serial.println("Motor command: " + perCommand + " us each, " + 1000000 / Math.max(perCommand, 1) + " per second");
    }

    private void measureMotion() {
        int rest = HUB.readSensor(LEFT);
        int started = CLOCK.millis();
        HUB.setMotorPower(LEFT, MOTOR_POWER);
        int moved = -1;
        while (moved < 0 && CLOCK.millis() - started < MOTION_TIMEOUT_MILLIS) {
            if (Math.abs(HUB.readSensor(LEFT) - rest) >= MOTION_DEGREES) {
                moved = CLOCK.millis() - started;
            }
        }
        HUB.brakeMotor(LEFT);
        Serial.println(moved < 0 ? "Motor A did not move: is a tacho motor on port A?"
                : "Motor A started moving after " + moved + " ms");
    }
}
