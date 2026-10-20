package io.github.jabrena.juno.api.thirdparty.lego.rcx;

import io.github.jabrena.juno.api.hardware.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.serial.Serial;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Checks the infrared link to an RCX brick, the step between {@link io.github.jabrena.juno.api.thirdparty.ir.InfraredLoopback}
 * and driving its motors: every two seconds it silently asks the switched-on brick for its battery voltage,
 * printing what came back. Wire the IR transmitter's DAT to pin 3 and the IR receiver's OUT to pin 2, both
 * with VCC and GND, facing the RCX's IR window from 10 to 50 cm.
 *
 * <p>Reading the output: {@code battery: 8900 mV} means both directions work; {@code no answer} means the
 * brick does not hear the transmitter or the receiver does not hear the reply (aim, distance, wiring, or the
 * brick off). Pick the board with {@code -Djuno.board}.
 */
public class RcxPing {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int PERIOD_MILLIS = 2000;
    private static final int START_MILLIS = 2000;

    public static void main(String[] args) {
        RcxLink link = new RcxLink();
        RcxRemote remote = new RcxRemote(link);
        RcxBrick brick = new RcxBrick(link);
        Serial.begin(BaudRate.BAUD_115200);
        remote.begin(RECEIVE_PIN, TRANSMIT_PIN);
        Delay.millis(START_MILLIS);
        Serial.println("RCX ping: switch the RCX on");

        while (true) {
            int battery = brick.batteryMillivolts();
            if (battery < 0) {
                Serial.println("battery: no answer");
            } else {
                Serial.print("battery: ");
                Serial.print(battery);
                Serial.println(" mV");
            }
            Delay.millis(PERIOD_MILLIS);
        }
    }
}
