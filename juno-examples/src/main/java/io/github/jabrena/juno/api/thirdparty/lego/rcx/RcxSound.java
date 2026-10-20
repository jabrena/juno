package io.github.jabrena.juno.api.thirdparty.lego.rcx;

import io.github.jabrena.juno.api.hardware.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.serial.Serial;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Plays the first five notes of "Ode to Joy" once on an RCX brick's own speaker over infrared. Wire an IR LED
 * with a series resistor to pin 3 and an IR receiver module to pin 2 (the brick acknowledges each command), both
 * pointed at the RCX. Pick the board with {@code -Djuno.board}.
 */
public class RcxSound {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int START_MILLIS = 10000;
    private static final int SERIAL_DRAIN_MILLIS = 20;
    private static final int NOTE_CENTISECONDS = 30;
    private static final int NOTE_GAP_MILLIS = 60;

    private static final int E4 = 330;
    private static final int F4 = 349;
    private static final int G4 = 392;

    public static void main(String[] args) {
        RcxLink link = new RcxLink();
        RcxRemote remote = new RcxRemote(link);
        RcxBrick brick = new RcxBrick(link);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        remote.begin(RECEIVE_PIN, TRANSMIT_PIN);
        Serial.println("Playing five tones");
        Delay.millis(SERIAL_DRAIN_MILLIS);
        play(E4, brick);
        play(E4, brick);
        play(F4, brick);
        play(G4, brick);
        play(G4, brick);
        Serial.println("Five-tone sequence finished");
    }

    private static void play(int frequency, RcxBrick brick) {
        boolean acknowledged = brick.playTone(frequency, NOTE_CENTISECONDS);
        Serial.print("Tone ");
        Serial.print(frequency);
        Serial.println(acknowledged ? " Hz: acknowledged" : " Hz: no acknowledgement");
        Delay.millis(NOTE_CENTISECONDS * 10 + NOTE_GAP_MILLIS);
    }
}
