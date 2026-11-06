package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxTouchSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/**
 * Reads an {@link RcxTouchSensor} on the RCX's input 1 from the Arduino over infrared and prints {@code Pressed} or
 * {@code Released} each time it changes. Works with LEGO's firmware or Juno's own.
 *
 * <p>Wiring: the IR transmitter module's DAT to pin 3 and the IR receiver module's OUT to pin 2, both with
 * VCC on 5V and GND, pointed at the RCX's IR window. Pick the board with {@code -Djuno.board}.
 */
public class RcxTouchSensorExample {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int POLL_MILLIS = 100;
    private static final int START_MILLIS = 2000;

    public static void main(String[] args) {
        RcxLink link = new RcxLink(new HX53InfraredEmitter(TRANSMIT_PIN), new HXM121InfraredReceiver(RECEIVE_PIN), ArduinoUno.getClock());
        RemoteRcx remote = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        RcxTouchSensor touch = new RcxTouchSensor(remote.getSensor1());
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        Serial.println("Touch sensor on input 1");

        boolean wasPressed = false;
        while (true) {
            boolean pressed = touch.isPressed();
            if (pressed != wasPressed) {
                Serial.println(pressed ? "Pressed" : "Released");
                wasPressed = pressed;
            }
            Delay.millis(POLL_MILLIS);
        }
    }
}
