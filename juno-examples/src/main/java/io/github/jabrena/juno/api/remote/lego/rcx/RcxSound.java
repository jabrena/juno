package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxAudio;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

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
        RemoteRcx remote = new RemoteRcx(new RcxLink(), RECEIVE_PIN, TRANSMIT_PIN);
        RcxAudio audio = remote.getAudio();
        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        Serial.println("Playing five tones");
        Delay.millis(SERIAL_DRAIN_MILLIS);
        play(E4, audio);
        play(E4, audio);
        play(F4, audio);
        play(G4, audio);
        play(G4, audio);
        Serial.println("Five-tone sequence finished");
    }

    private static void play(int frequency, RcxAudio audio) {
        boolean acknowledged = audio.playTone(frequency, NOTE_CENTISECONDS);
        Serial.print("Tone ");
        Serial.print(frequency);
        Serial.println(acknowledged ? " Hz: acknowledged" : " Hz: no acknowledgement");
        Delay.millis(NOTE_CENTISECONDS * 10 + NOTE_GAP_MILLIS);
    }
}
