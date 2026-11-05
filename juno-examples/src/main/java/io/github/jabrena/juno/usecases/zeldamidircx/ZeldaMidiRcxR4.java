package io.github.jabrena.juno.usecases.zeldamidircx;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLight;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.usecases.zeldapoliphonicrcx.RcxChannelAudio;
import io.github.jabrena.juno.util.Delay;

/**
 * Plays the Ocarina of Time title theme, {@code Ocarina_of_Time_Title_Theme.mid} on the Data Logger Shield's SD card,
 * on four RCX bricks from an UNO R4 WiFi.
 *
 * <p>The Grove infrared emitter is in the Grove Base Shield's D3 socket and the receiver in its D4 socket; the SD
 * card uses the Data Logger Shield's SPI pins with chip select on D10. The piano, strings, ocarina and violin
 * tracks play on RCX channels one to four, with each brick's activity lamp on output A. Each voice has its own
 * transposition, see {@link #toneHz}.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.usecases.zeldamidircx.ZeldaMidiRcxR4 \
 *   -Djuno.board=arduino-uno-r4-wifi
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-uno-r4-wifi -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoUnoR4WiFiBoard.class})
public final class ZeldaMidiRcxR4 {
    /**
     * Semitones added to each voice. Piano, strings and ocarina play an octave down. The violin's F2-D#3 pad would land
     * at 44-78 Hz an octave down, below what the RCX speaker can sound (it buzzes), so it plays an octave up instead:
     * F3-D#4, 175-311 Hz, the strings' register.
     */
    private static final int[] TRANSPOSE = {-12, -12, -12, 12};
    private static final int START_MILLIS = 15000;
    private static final int LAST_NOTE_MILLIS = 250;
    private static final int MIN_STEP_MILLIS = 10;
    private static final int NONE = Integer.MAX_VALUE;
    static final int REFERENCE_FREQUENCY_HZ = 440;
    static final int REFERENCE_DURATION_CENTISECONDS = 50;
    static final int[] ORCHESTRA_CHANNELS = {1, 2, 3, 4};
    /** The current physical wiring: each RCX brick has its activity lamp on output A. */
    private static final RcxLight INDICATOR_LIGHT = new RcxLight(RcxLight.PORT_A);

    private static final int[] NOTE_HZ = {8, 9, 9, 10, 10, 11, 12, 12, 13, 14, 15, 15, 16, 17, 18, 19,
        21, 22, 23, 24, 26, 28, 29, 31, 33, 35, 37, 39, 41, 44, 46, 49,
        52, 55, 58, 62, 65, 69, 73, 78, 82, 87, 92, 98, 104, 110, 117, 123,
        131, 139, 147, 156, 165, 175, 185, 196, 208, 220, 233, 247, 262, 277, 294, 311,
        330, 349, 370, 392, 415, 440, 466, 494, 523, 554, 587, 622, 659, 698, 740, 784,
        831, 880, 932, 988, 1047, 1109, 1175, 1245, 1319, 1397, 1480, 1568, 1661, 1760, 1865, 1976,
        2093, 2217, 2349, 2489, 2637, 2794, 2960, 3136, 3322, 3520, 3729, 3951, 4186, 4435, 4699, 4978,
        5274, 5588, 5920, 6272, 6645, 7040, 7459, 7902, 8372, 8870, 9397, 9956, 10548, 11175, 11840, 12544};

    private ZeldaMidiRcxR4() {
    }

    public static void main(String[] args) throws Exception {
        GroveBaseShield grove = new GroveBaseShield();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(grove.getD3());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(grove.getD4());
        SdCard sdCard = new DataLoggerShield().getSdCard();
        RcxLink link = new RcxLink(ArduinoUno.getClock());
        RcxChannelAudio audio = new RcxChannelAudio(link);
        ZeldaMidiRcxR4Midi score = new ZeldaMidiRcxR4Midi();
        int[] cursors = new int[ZeldaMidiRcxR4Midi.VOICES];
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        link.begin(receiver, emitter);
        Delay.millis(START_MILLIS);
        Serial.println("Grove shield IR: TX D3, RX D4");
        if (!sdCard.begin()) {
            Serial.println("SD card not found");
            return;
        }
        if (!score.load(sdCard)) {
            Serial.println("Cannot read MIDI file");
            return;
        }
        Serial.print("Parsed notes: ");
        Serial.println(score.noteCount());
        for (int channel : ORCHESTRA_CHANNELS) {
            audio.configureIndicator(channel, INDICATOR_LIGHT);
        }
        for (int channel : ORCHESTRA_CHANNELS) {
            audio.playTone(channel, REFERENCE_FREQUENCY_HZ, REFERENCE_DURATION_CENTISECONDS);
        }
        Delay.millis(REFERENCE_DURATION_CENTISECONDS * 10 + 100);
        play(score, audio, cursors);
        Serial.println("Zelda MIDI finished");
    }

    /** Plays every voice's notes due at each step, then waits until the next step's start. */
    private static void play(ZeldaMidiRcxR4Midi score, RcxChannelAudio audio, int[] cursors) {
        int now = nextStartMillis(score, cursors);
        while (now != NONE) {
            for (int voice = 0; voice < ZeldaMidiRcxR4Midi.VOICES; voice++) {
                playDueNotes(score, audio, cursors, voice, now);
            }
            int next = nextStartMillis(score, cursors);
            if (next != NONE) {
                int step = next - now;
                Delay.millis(step < MIN_STEP_MILLIS ? MIN_STEP_MILLIS : step);
            }
            now = next;
        }
    }

    private static int nextStartMillis(ZeldaMidiRcxR4Midi score, int[] cursors) {
        int next = NONE;
        for (int voice = 0; voice < ZeldaMidiRcxR4Midi.VOICES; voice++) {
            int cursor = cursors[voice];
            if (cursor < score.voiceCount(voice) && score.startMillis(voice, cursor) < next) {
                next = score.startMillis(voice, cursor);
            }
        }
        return next;
    }

    /** Sends {@code voice}'s notes starting at {@code now}, each lasting until that voice's next note. */
    private static void playDueNotes(ZeldaMidiRcxR4Midi score, RcxChannelAudio audio, int[] cursors, int voice,
            int now) {
        int count = score.voiceCount(voice);
        int cursor = cursors[voice];
        while (cursor < count && score.startMillis(voice, cursor) == now) {
            int frequency = toneHz(voice, score.noteNumber(voice, cursor));
            if (frequency > 0) {
                int endMillis = cursor + 1 < count ? score.startMillis(voice, cursor + 1) : now + LAST_NOTE_MILLIS;
                audio.playTone(ORCHESTRA_CHANNELS[voice], frequency, durationCentiseconds(endMillis - now));
            }
            cursor++;
        }
        cursors[voice] = cursor;
    }

    /** The frequency {@code voice} plays MIDI note {@code midiNote} at, or {@code 0} if transposed out of range. */
    static int toneHz(int voice, int midiNote) {
        int transposed = midiNote + TRANSPOSE[voice];
        return transposed >= 0 && transposed < NOTE_HZ.length ? NOTE_HZ[transposed] : 0;
    }

    static int durationCentiseconds(int toneMillis) {
        int centiseconds = toneMillis / 10;
        if (centiseconds < 1) {
            return 1;
        }
        return centiseconds > 255 ? 255 : centiseconds;
    }
}
