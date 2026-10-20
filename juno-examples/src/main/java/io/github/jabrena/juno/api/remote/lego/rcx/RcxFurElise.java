package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.rcx.RcxBrick;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxRemote;
import io.github.jabrena.juno.util.Delay;

/**
 * Plays the melody of Beethoven's "Für Elise" on an RCX brick's own speaker over infrared, forever, with a pause
 * between repeats: the theme twice, the middle section, the theme, the middle section again and the closing theme,
 * as the first part of the piece is played with its repeats (about 45 seconds). The brick sounds one tone at a
 * time, so it is the right hand's melody alone. Each note is one {@link RcxBrick#playTone} command; a command takes about 100 ms to send and
 * acknowledge, so every note is timed from when it should start rather than from the brick's answer, which
 * keeps the rhythm steady. The melody is two flash tables, so playing allocates nothing. It first switches the
 * brick's outputs off, since the brick keeps running whatever motor command an earlier program left it with.
 *
 * <p>Wiring: the IR transmitter's DAT to pin 3 and the IR receiver's OUT to pin 2, both with VCC and GND, facing
 * the switched-on RCX from 10 to 50 cm; the brick needs its firmware (see {@link RcxFirmware}). Pick the board
 * with {@code -Djuno.board}.
 */
public class RcxFurElise {
    private static final Clock CLOCK = MCU.getClock();
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    /** One sixteenth note, the melody's shortest step. */
    private static final int STEP_MILLIS = 160;
    /** A note sounds this much shorter than its step, so repeated notes stay distinct. */
    private static final int GAP_MILLIS = 20;
    private static final int PAUSE_SECONDS = 3;
    private static final int START_MILLIS = 2000;
    private static final int REST = 0;
    private static final int ALL_OUTPUTS = RcxBrick.OUTPUT_A | RcxBrick.OUTPUT_B | RcxBrick.OUTPUT_C;

    private static final int C4 = 262;
    private static final int E4 = 330;
    private static final int F4 = 349;
    private static final int G4 = 392;
    private static final int G_SHARP4 = 415;
    private static final int A4 = 440;
    private static final int B4 = 494;
    private static final int C5 = 523;
    private static final int D5 = 587;
    private static final int D_SHARP5 = 622;
    private static final int E5 = 659;
    private static final int F5 = 698;
    private static final int E6 = 1319;

    /**
     * The pitch of each note in hertz, {@code REST} for silence, part by part: theme (from 0), theme again (43),
     * middle (85), theme (114), middle (156), theme to the end (185).
     */
    private static final short[] NOTES = {
            E5, D_SHARP5, E5, D_SHARP5, E5, B4, D5, C5,
            A4, REST, C4, E4, A4, B4, REST, E4,
            G_SHARP4, B4, C5, REST, E4, E5, D_SHARP5, E5,
            D_SHARP5, E5, B4, D5, C5, A4, REST, C4,
            E4, A4, B4, REST, E4, C5, B4, A4,
            REST, E5, D_SHARP5, E5, D_SHARP5, E5, B4, D5,
            C5, A4, REST, C4, E4, A4, B4, REST,
            E4, G_SHARP4, B4, C5, REST, E4, E5, D_SHARP5,
            E5, D_SHARP5, E5, B4, D5, C5, A4, REST,
            C4, E4, A4, B4, REST, E4, C5, B4,
            A4, REST, B4, C5, D5, E5, G4, F5,
            E5, D5, F4, E5, D5, C5, E4, D5,
            C5, B4, REST, E4, E5, E4, REST, E5,
            E6, REST, D_SHARP5, E5, REST, D_SHARP5, E5, D_SHARP5,
            E5, D_SHARP5, E5, D_SHARP5, E5, B4, D5, C5,
            A4, REST, C4, E4, A4, B4, REST, E4,
            G_SHARP4, B4, C5, REST, E4, E5, D_SHARP5, E5,
            D_SHARP5, E5, B4, D5, C5, A4, REST, C4,
            E4, A4, B4, REST, E4, C5, B4, A4,
            REST, B4, C5, D5, E5, G4, F5, E5,
            D5, F4, E5, D5, C5, E4, D5, C5,
            B4, REST, E4, E5, E4, REST, E5, E6,
            REST, D_SHARP5, E5, REST, D_SHARP5, E5, D_SHARP5, E5,
            D_SHARP5, E5, D_SHARP5, E5, B4, D5, C5, A4,
            REST, C4, E4, A4, B4, REST, E4, G_SHARP4,
            B4, C5, REST, E4, E5, D_SHARP5, E5, D_SHARP5,
            E5, B4, D5, C5, A4, REST, C4, E4,
            A4, B4, REST, E4, C5, B4, A4,
    };

    /** The length of each note in sixteenths. */
    private static final byte[] STEPS = {
            1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 2, 1, 1,
            1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1,
            1, 1, 2, 1, 1, 1, 1, 2, 2, 1, 1, 1, 1, 1, 1, 1,
            1, 2, 1, 1, 1, 1, 2, 1, 1, 1, 1, 2, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 2, 1, 1, 1, 1,
            2, 1, 1, 1, 1, 3, 1, 1, 1, 3, 1, 1, 1, 3, 1, 1,
            1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 2, 1, 1,
            1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1,
            1, 1, 2, 1, 1, 1, 1, 2, 1, 1, 1, 1, 3, 1, 1, 1,
            3, 1, 1, 1, 3, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2,
            1, 1, 1, 1, 2, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 2, 1, 1, 1, 1, 2, 1, 1, 1, 1, 6,
    };

    private final RcxLink link;
    private final RcxRemote remote;
    private final RcxBrick brick;

    RcxFurElise(RcxLink link, RcxRemote remote, RcxBrick brick) {
        this.link = link;
        this.remote = remote;
        this.brick = brick;
    }

    public static void main(String[] args) {
        RcxLink link = new RcxLink();
        RcxRemote remote = new RcxRemote(link);
        RcxBrick brick = new RcxBrick(link);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        RcxFurElise app = new RcxFurElise(link, remote, brick);
        remote.begin(RECEIVE_PIN, TRANSMIT_PIN);
        int battery = brick.batteryMillivolts();
        while (battery < 0) {
            Serial.println("The RCX did not answer: is it on and in range, with the IR receiver wired?");
            Delay.millis(1000);
            battery = brick.batteryMillivolts();
        }
        brick.motorOff(ALL_OUTPUTS);

        while (true) {
            Serial.println("Fur Elise, Ludwig van Beethoven");
            app.play();
            Delay.seconds(PAUSE_SECONDS);
        }
    }

    /** Plays the melody once, each note starting on its step whatever the infrared round trip took. */
    private void play() {
        int noteStart = CLOCK.millis();
        for (int i = 0; i < NOTES.length; i++) {
            int length = STEPS[i] * STEP_MILLIS;
            if (NOTES[i] != REST) {
                brick.playTone(NOTES[i], (length - GAP_MILLIS) / 10);
            }
            noteStart += length;
            int remaining = noteStart - CLOCK.millis();
            if (remaining > 0) {
                Delay.millis(remaining);
            }
        }
    }
}
