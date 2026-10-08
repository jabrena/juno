package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Plays sound on a Scout brick's own speaker over infrared, after printing its battery voltage over Serial: first the six built-in sounds, then
 * "Ode to Joy" as a series of tones of chosen pitch and length, forever. Wire an IR LED with a series
 * resistor to pin 3 and an IR receiver module to pin 2 (the brick acknowledges tone and battery commands), both
 * pointed at the Scout. Pick the board with {@code -Djuno.board}.
 */
public class ScoutSound {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int SOUNDS = 6;
    private static final int NOTES = 15;
    private static final int SOUND_PAUSE_MILLIS = 1500;
    private static final int NOTE_CENTISECONDS = 30;
    private static final int NOTE_GAP_MILLIS = 60;
    private static final int TUNE_PAUSE_SECONDS = 3;

    private static final int C4 = 262;
    private static final int D4 = 294;
    private static final int E4 = 330;
    private static final int F4 = 349;
    private static final int G4 = 392;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        ScoutSound app = new ScoutSound();
        ScoutRemote.begin(RECEIVE_PIN, TRANSMIT_PIN);
        Serial.println("Scout battery: " + ScoutRemote.batteryMillivolts() + " mV");

        while (true) {
            for (int sound = 0; sound < SOUNDS; sound++) {
                Serial.println("Built-in sound " + sound);
                ScoutRemote.playSound(sound);
                Delay.millis(SOUND_PAUSE_MILLIS);
            }
            Serial.println("Ode to Joy");
            app.playTune();
            Delay.seconds(TUNE_PAUSE_SECONDS);
        }
    }

    /** Ode to Joy: E E F G, G F E D, C C D E, E D D. */
    private void playTune() {
        int[] notes = {E4, E4, F4, G4, G4, F4, E4, D4, C4, C4, D4, E4, E4, D4, D4};
        for (int i = 0; i < NOTES; i++) {
            ScoutRemote.playTone(notes[i], NOTE_CENTISECONDS);
            Delay.millis(NOTE_CENTISECONDS * 10 + NOTE_GAP_MILLIS);
        }
    }
}
