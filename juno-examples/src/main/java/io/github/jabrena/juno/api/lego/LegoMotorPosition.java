package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * Reads a LEGO tacho motor's rotation sensor to turn it exactly one revolution forwards, then one
 * backwards, forever, printing the position it reports over Serial. Works with any motor that has
 * a rotation sensor (BOOST, Technic or SPIKE motors) on port A of a Technic Hub, Move Hub or City
 * Hub. Pick the board with {@code -Djuno.board}.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class LegoMotorPosition {
    private static final int MOTOR = PoweredUpHub.PORT_A;
    private static final int POWER = 30;
    private static final int REVOLUTION_DEGREES = 360;
    private static final int FIRST_REPORT_MILLIS = 500;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            if (!PoweredUpHub.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                PoweredUpHub.connect(0);
                // A new connection reports nothing until a mode is enabled again.
                PoweredUpHub.enableSensor(MOTOR, PoweredUpHub.MODE_MOTOR_POSITION);
                // Reports arrive while readSensor runs; give the hub time to send the current position.
                int started = Clock.millis();
                while (Clock.millis() - started < FIRST_REPORT_MILLIS) {
                    PoweredUpHub.readSensor(MOTOR);
                }
            }
            turnTo(PoweredUpHub.readSensor(MOTOR) + REVOLUTION_DEGREES, PoweredUpHub.COLOR_GREEN);
            turnTo(PoweredUpHub.readSensor(MOTOR) - REVOLUTION_DEGREES, PoweredUpHub.COLOR_BLUE);
        }
    }

    private static void turnTo(int target, int color) {
        PoweredUpHub.setLedColor(color);
        int position = PoweredUpHub.readSensor(MOTOR);
        int direction = target > position ? 1 : -1;
        PoweredUpHub.setMotorPower(MOTOR, direction * POWER);
        while (PoweredUpHub.isConnected() && (target - position) * direction > 0) {
            position = PoweredUpHub.readSensor(MOTOR);
        }
        PoweredUpHub.brakeMotor(MOTOR);
        Serial.println("Position: " + position);
    }
}
