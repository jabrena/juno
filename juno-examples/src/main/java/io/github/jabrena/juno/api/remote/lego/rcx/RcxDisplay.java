package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLcd;
import io.github.jabrena.juno.remote.lego.rcx.RcxLightSensor;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/**
 * Writes numbers on the RCX's display from the Arduino: a countdown from 10 to -10, one number a second, then the
 * light sensor on input 3 in percent, twice a second, forever, echoing each number over Serial. Needs Juno's own RCX
 * firmware ({@code RcxRemoteFirmware}), which shows the numbers in place of {@code JUno}; LEGO's firmware does
 * not know the command.
 *
 * <p>Wiring: the IR transmitter module's DAT to pin 3 and the IR receiver module's OUT to pin 2, both with
 * VCC on 5V and GND, pointed at the RCX's IR window. Pick the board with {@code -Djuno.board}.
 */
@SuppressWarnings("java:S2189")
public class RcxDisplay {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int COUNTDOWN_FROM = 10;
    private static final int COUNT_MILLIS = 1000;
    private static final int LIGHT_MILLIS = 500;
    private static final int START_MILLIS = 2000;
    private static final int RETRY_MILLIS = 1000;

    public static void main(String[] args) {
        RemoteRcx remote = new RemoteRcx(new RcxLink(), RECEIVE_PIN, TRANSMIT_PIN);
        RcxLcd lcd = remote.getLcd();
        RcxLightSensor light = new RcxLightSensor(remote.getSensor3());
        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        while (!lcd.showNumber(COUNTDOWN_FROM)) {
            Serial.println("No answer: is Juno's firmware on the RCX, and is it on and in range?");
            Delay.millis(RETRY_MILLIS);
        }

        for (int number = COUNTDOWN_FROM; number >= -COUNTDOWN_FROM; number--) {
            lcd.showNumber(number);
            Serial.print("display ");
            Serial.println(number);
            Delay.millis(COUNT_MILLIS);
        }

        light.begin();
        Serial.println("Showing the light sensor on input 3");
        while (true) {
            int percent = light.lightPercent();
            if (percent >= 0) {
                lcd.showNumber(percent);
                Serial.print("display light ");
                Serial.print(percent);
                Serial.println(" %");
            }
            Delay.millis(LIGHT_MILLIS);
        }
    }
}
