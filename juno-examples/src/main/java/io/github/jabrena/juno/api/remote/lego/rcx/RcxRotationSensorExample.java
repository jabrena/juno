package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
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

    public static void main(String[] args) {
        RcxLink link = new RcxLink(new HX53InfraredEmitter(TRANSMIT_PIN), new HXM121InfraredReceiver(RECEIVE_PIN), ArduinoUno.getClock());
        RemoteRcx remote = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        RcxRotationSensor rotation = new RcxRotationSensor(remote.getSensor1());
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
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
