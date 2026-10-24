package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxRotationSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/**
 * Reads an {@link RcxRotationSensor} on the RCX's input 1 from the Arduino over infrared: sets its count to zero at
 * startup, then prints the steps (16 per turn) and the angle in degrees each time the axle turns. Works with LEGO's
 * firmware or Juno's own.
 *
 * <p>Wiring: the IR transmitter module's DAT to pin 3 and the IR receiver module's OUT to pin 2, both with
 * VCC on 5V and GND, pointed at the RCX's IR window. Pick the board with {@code -Djuno.board}.
 */
public class RcxRotationSensorExample {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int POLL_MILLIS = 200;
    private static final int START_MILLIS = 2000;
    private static final int RETRY_MILLIS = 1000;

    public static void main(String[] args) {
        RemoteRcx remote = new RemoteRcx(new RcxLink(), RECEIVE_PIN, TRANSMIT_PIN);
        RcxRotationSensor rotation = new RcxRotationSensor(remote.getSensor1());
        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        while (!rotation.begin()) {
            Serial.println("The RCX did not answer: is it on and in range, with the IR receiver wired?");
            Delay.millis(RETRY_MILLIS);
        }
        rotation.reset();
        Serial.println("Rotation sensor on input 1: turn the axle");

        int lastSteps = 0;
        while (true) {
            int steps = rotation.steps();
            if (steps != lastSteps) {
                Serial.print("steps ");
                Serial.print(steps);
                Serial.print("  degrees ");
                Serial.println(rotation.degrees());
                lastSteps = steps;
            }
            Delay.millis(POLL_MILLIS);
        }
    }
}
