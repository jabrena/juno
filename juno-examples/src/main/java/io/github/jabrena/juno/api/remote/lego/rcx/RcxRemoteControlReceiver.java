package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxRemoteControl;
import io.github.jabrena.juno.util.Delay;

/** Prints a message when the UNO R4 receives a button signal from a LEGO Mindstorms 9738 RCX remote. */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class RcxRemoteControlReceiver {
    private static final int RECEIVE_PIN = 2;
    private static final int POLL_MILLIS = 20;
    private static final int START_MILLIS = 2000;

    private RcxRemoteControlReceiver() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RcxRemoteControl remote = new RcxRemoteControl(new HXM121InfraredReceiver(RECEIVE_PIN), ArduinoUno.getClock());
        Delay.millis(START_MILLIS);
        Serial.println("Listening for the LEGO RCX remote");

        int previousButtons = 0;
        while (true) {
            int buttons = remote.buttons();
            if (buttons != 0 && buttons != previousButtons) {
                Serial.println("Received signal from LEGO RCX remote");
                Serial.print("button mask: ");
                Serial.println(buttons);
            }
            previousButtons = buttons;
            Delay.millis(POLL_MILLIS);
        }
    }
}
