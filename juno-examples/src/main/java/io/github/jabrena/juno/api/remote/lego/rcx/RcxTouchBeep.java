package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxAudio;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxSensor;
import io.github.jabrena.juno.remote.lego.rcx.RcxTouchSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/**
 * Beeps on the RCX each time the touch sensor on input 2 is pressed: the Arduino polls the sensor over infrared and,
 * when it goes from released to pressed, asks the brick to play its beep, printing each press over Serial. Holding
 * the sensor down beeps once; release it to beep again. Works with LEGO's firmware or Juno's own.
 *
 * <p>Wiring: the IR transmitter module's DAT to pin 3 and the IR receiver module's OUT to pin 2, both with
 * VCC on 5V and GND, pointed at the RCX's IR window. Pick the board with {@code -Djuno.board}.
 */
public class RcxTouchBeep {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int POLL_MILLIS = 50;
    private static final int START_MILLIS = 2000;
    private static final int RETRY_MILLIS = 1000;

    public static void main(String[] args) {
        RemoteRcx remote = new RemoteRcx(new RcxLink(), RECEIVE_PIN, TRANSMIT_PIN);
        RcxSensor touch = remote.getSensor2();
        RcxTouchSensor touchSensor = new RcxTouchSensor(touch);
        RcxAudio audio = remote.getAudio();
        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        while (!touchSensor.begin()) {
            Serial.println("The RCX did not answer: is it on and in range, with the IR receiver wired?");
            Delay.millis(RETRY_MILLIS);
        }
        Serial.println("Press the touch sensor on input 2");

        boolean wasPressed = false;
        int presses = 0;
        while (true) {
            // The port's raw value tells a missed reply (-1) from a release (0), so a lost reply is no new press.
            int reading = touch.readValue();
            if (reading >= 0) {
                boolean pressed = reading == 1;
                if (pressed && !wasPressed) {
                    presses++;
                    audio.playSound(RcxAudio.SOUND_BEEP);
                    Serial.print("Pressed: beep ");
                    Serial.println(presses);
                }
                wasPressed = pressed;
            }
            Delay.millis(POLL_MILLIS);
        }
    }
}
