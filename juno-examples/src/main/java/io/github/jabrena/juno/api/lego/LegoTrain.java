package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * Drives a LEGO Powered Up train back and forth over Bluetooth LE: connects to the first hub that
 * is switched on (press its green button), then ramps the train motor on port A up to cruising
 * speed, brakes, and does the same in reverse, forever. The hub's LED shows the direction (green
 * forwards, blue backwards, red while stopped). If the hub switches off or goes out of range, the
 * program waits for it to come back.
 *
 * <p>Needs the {@code ArduinoBLE} library ({@code arduino-cli lib install ArduinoBLE}; 2.1.0 or
 * newer on the UNO Q) and a hub with a train or other Powered Up motor on port A, e.g. a City Hub
 * (88009) with a train motor (88011). Pick the board with {@code -Djuno.board}.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class LegoTrain {
    private static final int MOTOR = PoweredUpHub.PORT_A;
    private static final int CRUISE_POWER = 60;
    private static final int RAMP_STEP = 10;
    private static final int RAMP_DELAY_MILLIS = 200;
    private static final int CRUISE_MILLIS = 3000;
    private static final int STOP_MILLIS = 2000;

    public static void main(String[] args) {
        Serial.begin(9600);

        while (true) {
            if (!PoweredUpHub.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                PoweredUpHub.connect(0);
                Serial.print("Connected to hub type ");
                Serial.println(PoweredUpHub.hubType());
            }
            run(1, PoweredUpHub.COLOR_GREEN);
            run(-1, PoweredUpHub.COLOR_BLUE);
        }
    }

    private static void run(int direction, int color) {
        PoweredUpHub.setLedColor(color);
        for (int power = RAMP_STEP; power <= CRUISE_POWER; power += RAMP_STEP) {
            PoweredUpHub.setMotorPower(MOTOR, direction * power);
            Delay.millis(RAMP_DELAY_MILLIS);
        }
        Delay.millis(CRUISE_MILLIS);
        PoweredUpHub.brakeMotor(MOTOR);
        PoweredUpHub.setLedColor(PoweredUpHub.COLOR_RED);
        Delay.millis(STOP_MILLIS);
    }
}
