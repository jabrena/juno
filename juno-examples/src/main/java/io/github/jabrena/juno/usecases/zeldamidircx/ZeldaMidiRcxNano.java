package io.github.jabrena.juno.usecases.zeldamidircx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLight;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.usecases.zeldapoliphonicrcx.RcxChannelAudio;
import io.github.jabrena.juno.util.Delay;

import java.io.InputStream;

@Board({ArduinoNanoEsp32Board.class})
public final class ZeldaMidiRcxNano {
    private static final String MIDI_FILE = "z5zelda.mid";
    private static final int CHANNEL_1 = 1;
    private static final int CHANNEL_2 = 2;
    private static final int CHANNEL_3 = 3;
    private static final int TRANSPOSE = -12;
    private static final int MAX_FILE_BYTES = 20000;
    private static final int START_MILLIS = 15000;
    static final int REFERENCE_FREQUENCY_HZ = 440;
    static final int REFERENCE_DURATION_CENTISECONDS = 50;
    static final int[] ORCHESTRA_CHANNELS = {CHANNEL_1, CHANNEL_2, CHANNEL_3};




    private static final RcxLight LIGHT_1 = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight LIGHT_2 = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight LIGHT_3 = new RcxLight(RcxLight.PORT_A);

    private ZeldaMidiRcxNano() {
    }

    private static final int[] NOTE_HZ = { 8, 9, 9, 10, 10, 11, 12, 12, 13, 14, 15, 15, 16, 17, 18, 19,
        21, 22, 23, 24, 26, 28, 29, 31, 33, 35, 37, 39, 41, 44, 46, 49,
        52, 55, 58, 62, 65, 69, 73, 78, 82, 87, 92, 98, 104, 110, 117, 123,
        131, 139, 147, 156, 165, 175, 185, 196, 208, 220, 233, 247, 262, 277, 294, 311,
        330, 349, 370, 392, 415, 440, 466, 494, 523, 554, 587, 622, 659, 698, 740, 784,
        831, 880, 932, 988, 1047, 1109, 1175, 1245, 1319, 1397, 1480, 1568, 1661, 1760, 1865, 1976,
        2093, 2217, 2349, 2489, 2637, 2794, 2960, 3136, 3322, 3520, 3729, 3951, 4186, 4435, 4699, 4978,
        5274, 5588, 5920, 6272, 6645, 7040, 7459, 7902, 8372, 8870, 9397, 9956, 10548, 11175, 11840, 12544 };

    public static void main(String[] args) throws Exception {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(ArduinoNano.getClock());
        RcxChannelAudio audio = new RcxChannelAudio(link);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        link.begin(receiver, emitter);
        Delay.millis(START_MILLIS);
        Serial.println("Nano carrier IR: TX A0, RX A5");
        Serial.println("Reading MIDI from SD card");

        SdCard sdCard = carrier.getSdCard();
        if (!sdCard.begin()) {
            Serial.println("SD card not found");
            return;
        }
        InputStream in = sdCard.open(MIDI_FILE);
        if (in == null) {
            Serial.println("Cannot open MIDI file");
            return;
        }
        ZeldaMidiRcxNanoMidi.midiData = new byte[MAX_FILE_BYTES];
        int fileSize = 0;
        int b;
        while (fileSize < MAX_FILE_BYTES) {
            b = in.read();
            if (b < 0) {
                break;
            }
            ZeldaMidiRcxNanoMidi.midiData[fileSize] = (byte) b;
            fileSize++;
        }
        in.close();
        if (fileSize == 0) {
            Serial.println("MIDI file empty");
            return;
        }
        ZeldaMidiRcxNanoMidi.parseMidi();

        Serial.println("Parsed notes: " + ZeldaMidiRcxNanoMidi.noteCount());
        audio.configureIndicator(CHANNEL_1, LIGHT_1);
        audio.configureIndicator(CHANNEL_2, LIGHT_2);
        audio.configureIndicator(CHANNEL_3, LIGHT_3);
        audio.playTone(CHANNEL_1, REFERENCE_FREQUENCY_HZ, REFERENCE_DURATION_CENTISECONDS);
        audio.playTone(CHANNEL_2, REFERENCE_FREQUENCY_HZ, REFERENCE_DURATION_CENTISECONDS);
        audio.playTone(CHANNEL_3, REFERENCE_FREQUENCY_HZ, REFERENCE_DURATION_CENTISECONDS);
        Delay.millis(REFERENCE_DURATION_CENTISECONDS * 10 + 100);

        play(audio);
        Serial.println("Zelda MIDI finished");
    }

    private static void play(RcxChannelAudio audio) {
        int idx1 = 0;
        int idx2 = 0;
        int idx3 = 0;

        while (idx1 < ZeldaMidiRcxNanoMidi.noteCount1 || idx2 < ZeldaMidiRcxNanoMidi.noteCount2 || idx3 < ZeldaMidiRcxNanoMidi.noteCount3) {
            int nextTick = Integer.MAX_VALUE;
            if (idx1 < ZeldaMidiRcxNanoMidi.noteCount1 && ZeldaMidiRcxNanoMidi.noteTick1[idx1] < nextTick) {
                nextTick = ZeldaMidiRcxNanoMidi.noteTick1[idx1];
            }
            if (idx2 < ZeldaMidiRcxNanoMidi.noteCount2 && ZeldaMidiRcxNanoMidi.noteTick2[idx2] < nextTick) {
                nextTick = ZeldaMidiRcxNanoMidi.noteTick2[idx2];
            }
            if (idx3 < ZeldaMidiRcxNanoMidi.noteCount3 && ZeldaMidiRcxNanoMidi.noteTick3[idx3] < nextTick) {
                nextTick = ZeldaMidiRcxNanoMidi.noteTick3[idx3];
            }
            if (nextTick == Integer.MAX_VALUE) {
                break;
            }

            while (idx1 < ZeldaMidiRcxNanoMidi.noteCount1 && ZeldaMidiRcxNanoMidi.noteTick1[idx1] == nextTick) {
                int midiNote = ZeldaMidiRcxNanoMidi.noteNum1[idx1] + TRANSPOSE;
                if (midiNote >= 0 && midiNote < 128) {
                    int freq = NOTE_HZ[midiNote];
                    if (freq > 0) {
                        int startMs = ZeldaMidiRcxNanoMidi.ticksToMillis(ZeldaMidiRcxNanoMidi.noteTick1[idx1]);
                        int endMs;
                        if (idx1 + 1 < ZeldaMidiRcxNanoMidi.noteCount1) {
                            endMs = ZeldaMidiRcxNanoMidi.ticksToMillis(ZeldaMidiRcxNanoMidi.noteTick1[idx1 + 1]);
                        } else {
                            endMs = startMs + 250;
                        }
                        int durMs = endMs - startMs;
                        if (durMs < 10) {
                            durMs = 10;
                        }
                        int durCs = durMs / 10;
                        if (durCs < 1) {
                            durCs = 1;
                        }
                        if (durCs > 255) {
                            durCs = 255;
                        }
                        audio.playTone(CHANNEL_1, freq, durCs);
                    }
                }
                idx1++;
            }

            while (idx2 < ZeldaMidiRcxNanoMidi.noteCount2 && ZeldaMidiRcxNanoMidi.noteTick2[idx2] == nextTick) {
                int midiNote = ZeldaMidiRcxNanoMidi.noteNum2[idx2] + TRANSPOSE;
                if (midiNote >= 0 && midiNote < 128) {
                    int freq = NOTE_HZ[midiNote];
                    if (freq > 0) {
                        int startMs = ZeldaMidiRcxNanoMidi.ticksToMillis(ZeldaMidiRcxNanoMidi.noteTick2[idx2]);
                        int endMs;
                        if (idx2 + 1 < ZeldaMidiRcxNanoMidi.noteCount2) {
                            endMs = ZeldaMidiRcxNanoMidi.ticksToMillis(ZeldaMidiRcxNanoMidi.noteTick2[idx2 + 1]);
                        } else {
                            endMs = startMs + 250;
                        }
                        int durMs = endMs - startMs;
                        if (durMs < 10) {
                            durMs = 10;
                        }
                        int durCs = durMs / 10;
                        if (durCs < 1) {
                            durCs = 1;
                        }
                        if (durCs > 255) {
                            durCs = 255;
                        }
                        audio.playTone(CHANNEL_2, freq, durCs);
                    }
                }
                idx2++;
            }

            while (idx3 < ZeldaMidiRcxNanoMidi.noteCount3 && ZeldaMidiRcxNanoMidi.noteTick3[idx3] == nextTick) {
                int midiNote = ZeldaMidiRcxNanoMidi.noteNum3[idx3] + TRANSPOSE;
                if (midiNote >= 0 && midiNote < 128) {
                    int freq = NOTE_HZ[midiNote];
                    if (freq > 0) {
                        int startMs = ZeldaMidiRcxNanoMidi.ticksToMillis(ZeldaMidiRcxNanoMidi.noteTick3[idx3]);
                        int endMs;
                        if (idx3 + 1 < ZeldaMidiRcxNanoMidi.noteCount3) {
                            endMs = ZeldaMidiRcxNanoMidi.ticksToMillis(ZeldaMidiRcxNanoMidi.noteTick3[idx3 + 1]);
                        } else {
                            endMs = startMs + 250;
                        }
                        int durMs = endMs - startMs;
                        if (durMs < 10) {
                            durMs = 10;
                        }
                        int durCs = durMs / 10;
                        if (durCs < 1) {
                            durCs = 1;
                        }
                        if (durCs > 255) {
                            durCs = 255;
                        }
                        audio.playTone(CHANNEL_3, freq, durCs);
                    }
                }
                idx3++;
            }

            int nextTick2 = Integer.MAX_VALUE;
            if (idx1 < ZeldaMidiRcxNanoMidi.noteCount1 && ZeldaMidiRcxNanoMidi.noteTick1[idx1] < nextTick2) {
                nextTick2 = ZeldaMidiRcxNanoMidi.noteTick1[idx1];
            }
            if (idx2 < ZeldaMidiRcxNanoMidi.noteCount2 && ZeldaMidiRcxNanoMidi.noteTick2[idx2] < nextTick2) {
                nextTick2 = ZeldaMidiRcxNanoMidi.noteTick2[idx2];
            }
            if (idx3 < ZeldaMidiRcxNanoMidi.noteCount3 && ZeldaMidiRcxNanoMidi.noteTick3[idx3] < nextTick2) {
                nextTick2 = ZeldaMidiRcxNanoMidi.noteTick3[idx3];
            }
            if (nextTick2 != Integer.MAX_VALUE) {
                int delayMs = ZeldaMidiRcxNanoMidi.ticksToMillis(nextTick2) - ZeldaMidiRcxNanoMidi.ticksToMillis(nextTick);
                if (delayMs < 10) {
                    delayMs = 10;
                }
                Delay.millis(delayMs);
            }
        }
    }


    static int durationCentiseconds(int toneMillis) {
        return ZeldaMidiRcxNanoMidi.durationCentiseconds(toneMillis);
    }

    static int noteCount() {
        return ZeldaMidiRcxNanoMidi.noteCount();
    }
}
