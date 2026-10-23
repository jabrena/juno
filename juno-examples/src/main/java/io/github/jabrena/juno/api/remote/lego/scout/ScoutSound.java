package io.github.jabrena.juno.api.remote.lego.scout;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.remote.lego.scout.RemoteScout;
import io.github.jabrena.juno.util.Delay;

/**
 * Selects each Scout sound group and activates the brick's SOUND remote-control command over infrared. Scout
 * firmware does not execute the RCX arbitrary-tone opcode as a direct IR command, so remote control can trigger its
 * predefined sound effects but cannot play a pitched melody. Wire an IR LED with a series resistor to pin 3 and an IR
 * receiver module to pin 2, both pointed at the Scout. Pick the board with {@code -Djuno.board}.
 */
public class ScoutSound {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int SERIAL_STARTUP_MILLIS = 500;
    private static final int REMOTE_REPEATS = 3;
    private static final int REMOTE_REPEAT_MILLIS = 70;
    private static final int SOUND_PAUSE_SECONDS = 3;

    public static void main(String[] args) {
        RemoteScout scout = new RemoteScout();
        Serial.begin(BaudRate.BAUD_115200);
        scout.begin(RECEIVE_PIN, TRANSMIT_PIN);
        Delay.millis(SERIAL_STARTUP_MILLIS);
        scout.setMode(RemoteScout.MODE_POWER);
        Delay.millis(500);
        scout.unmuteSound();
        Delay.millis(200);

        while (true) {
            for (int soundSet = RemoteScout.SOUND_SET_BASIC;
                    soundSet <= RemoteScout.SOUND_SET_SCIENCE;
                    soundSet++) {
                Serial.print("Scout sound group ");
                Serial.println(soundSet);
                scout.selectSoundSet(soundSet);
                Delay.millis(200);
                for (int repeat = 0; repeat < REMOTE_REPEATS; repeat++) {
                    scout.sendButtons(RemoteRcx.SOUND);
                    Delay.millis(REMOTE_REPEAT_MILLIS);
                }
                scout.sendButtons(0);
                Delay.seconds(SOUND_PAUSE_SECONDS);
            }
        }
    }
}
