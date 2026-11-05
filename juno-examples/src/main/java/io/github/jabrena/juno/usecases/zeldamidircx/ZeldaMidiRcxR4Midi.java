package io.github.jabrena.juno.usecases.zeldamidircx;

import io.github.jabrena.juno.hardware.device.sd.SdCard;

import java.io.IOException;
import java.io.InputStream;

/**
 * Four note tracks of the Ocarina of Time title theme, a format-1 MIDI file on an SD card, parsed into fixed arrays
 * sized for the UNO R4 WiFi's 8 KiB arena. Unlike {@link ZeldaMidiRcxNanoMidi}, the whole file is never buffered: one
 * chunk at a time passes through a buffer the size of the largest kept track, the other tracks are skipped as they
 * stream past, and every note's start tick becomes milliseconds once the tempo map has been read. Create one at
 * startup and reuse it.
 */
public final class ZeldaMidiRcxR4Midi {
    /**
     * The MIDI track behind each voice: piano, strings, ocarina and violin. Tracks 4 and 5 are skipped: they repeat
     * the ocarina 0.22 s and 0.43 s later as an echo, which would make two bricks play the same line.
     */
    private static final int[] VOICE_TRACKS = {1, 2, 3, 6};
    /** Voices kept, one per brick. */
    static final int VOICES = 4;
    /** Notes kept across all voices; the title theme's four voices have 432. */
    static final int MAX_NOTES = 448;
    /** The file read from the card's root directory. */
    static final String MIDI_FILE = "Ocarina_of_Time_Title_Theme.mid";
    /** The largest track kept; the title theme's strings track is 2,169 bytes. */
    static final int MAX_TRACK_BYTES = 2176;
    private static final int MAX_TEMPOS = 128;
    private static final int CHUNK_HEADER_BYTES = 8;
    private static final int HEADER_BYTES = 6;
    private static final int DEFAULT_MICROS_PER_QUARTER = 500000;

    private final byte[] buffer = new byte[MAX_TRACK_BYTES];
    /** Start ticks while parsing, start milliseconds afterwards. */
    private final int[] noteMillis = new int[MAX_NOTES];
    private final byte[] noteNumbers = new byte[MAX_NOTES];
    private final int[] voiceStart = new int[VOICES];
    private final int[] voiceCount = new int[VOICES];
    private final int[] tempoTicks = new int[MAX_TEMPOS];
    private final int[] tempoMicros = new int[MAX_TEMPOS];
    private int tempoCount;
    private int noteCount;
    private int division;
    private int cursor;
    private int end;

    /**
     * Reads {@link #MIDI_FILE} from {@code card}: false if it cannot be opened, is not a complete MIDI file, or has a
     * kept track larger than {@link #MAX_TRACK_BYTES}. The name is a constant because the board opens only
     * compile-time file names.
     */
    public boolean load(SdCard card) throws IOException {
        tempoCount = 0;
        noteCount = 0;
        division = 0;
        for (int voice = 0; voice < VOICES; voice++) {
            voiceStart[voice] = 0;
            voiceCount[voice] = 0;
        }
        try (InputStream in = card.open(MIDI_FILE)) {
            if (in == null) {
                return false;
            }
            // Chunk 0 is the header; chunk n is MIDI track n - 1, track 0 being the tempo track.
            int chunks = 1;
            for (int chunk = 0; chunk < chunks; chunk++) {
                int got = 0;
                while (got < CHUNK_HEADER_BYTES) {
                    int n = in.read(buffer, got, CHUNK_HEADER_BYTES - got);
                    if (n <= 0) {
                        return false;
                    }
                    got += n;
                }
                boolean header = chunk == 0;
                if (!hasTag(header ? 'h' : 'r', header ? 'd' : 'k')) {
                    return false;
                }
                int length = int32At(4);
                int voice = voiceOf(chunk - 1);
                boolean keep = chunk <= 1 || voice >= 0;
                if (length < 0 || keep && length > buffer.length || header && length < HEADER_BYTES) {
                    return false;
                }
                int offset = 0;
                int remaining = length;
                while (remaining > 0) {
                    int room = buffer.length - offset;
                    int n = in.read(buffer, offset, remaining < room ? remaining : room);
                    if (n <= 0) {
                        return false;
                    }
                    remaining -= n;
                    if (keep) {
                        offset += n;
                    }
                }
                if (header) {
                    chunks = 1 + uint16At(2);
                    division = uint16At(4);
                } else if (keep) {
                    if (voice >= 0) {
                        voiceStart[voice] = noteCount;
                    }
                    parseTrack(voice, length);
                    if (voice >= 0) {
                        voiceCount[voice] = noteCount - voiceStart[voice];
                    }
                }
            }
        }
        if (division <= 0) {
            return false;
        }
        convertTicksToMillis();
        return true;
    }

    /** The voice that plays MIDI track {@code track}, or {@code -1} if the track is not kept as a voice. */
    private static int voiceOf(int track) {
        for (int voice = 0; voice < VOICES; voice++) {
            if (VOICE_TRACKS[voice] == track) {
                return voice;
            }
        }
        return -1;
    }

    /** Notes kept in every voice. */
    public int noteCount() {
        return noteCount;
    }

    /** Notes kept in {@code voice}, {@code 0..3}. */
    public int voiceCount(int voice) {
        return voiceCount[voice];
    }

    /** When note {@code index} of {@code voice} starts, in milliseconds from the beginning of the piece. */
    public int startMillis(int voice, int index) {
        return noteMillis[voiceStart[voice] + index];
    }

    /** The MIDI note number, {@code 0..127}, of note {@code index} of {@code voice}. */
    public int noteNumber(int voice, int index) {
        return noteNumbers[voiceStart[voice] + index] & 0x7F;
    }

    /** Collects the tempo changes, and the note-ons of {@code voice} unless it is negative (the tempo track). */
    private void parseTrack(int voice, int length) {
        cursor = 0;
        end = length;
        int tick = 0;
        int runningStatus = 0;
        while (cursor < end) {
            tick += readVarLen();
            if (cursor >= end) {
                return;
            }
            int status = readByte();
            int firstData = -1;
            if (status < 0x80) {
                firstData = status;
                status = runningStatus;
            } else if (status < 0xF0) {
                runningStatus = status;
            }
            handleEvent(voice, tick, status, firstData);
        }
    }

    private void handleEvent(int voice, int tick, int status, int firstData) {
        int kind = status & 0xF0;
        if (status == 0xFF) {
            int type = readByte();
            int length = readVarLen();
            if (type == 0x51 && length == 3 && tempoCount < MAX_TEMPOS) {
                tempoTicks[tempoCount] = tick;
                tempoMicros[tempoCount] = (readByte() << 16) | (readByte() << 8) | readByte();
                tempoCount++;
            } else {
                cursor += length;
            }
        } else if (status == 0xF0 || status == 0xF7) {
            // Not cursor += readVarLen(): Java would add to cursor's value from before the length bytes were read.
            int length = readVarLen();
            cursor += length;
        } else if (kind == 0x80 || kind == 0x90 || kind == 0xA0 || kind == 0xB0 || kind == 0xE0) {
            int note = firstData >= 0 ? firstData : readByte();
            int velocity = readByte();
            if (kind == 0x90 && velocity > 0 && voice >= 0 && noteCount < MAX_NOTES) {
                noteMillis[noteCount] = tick;
                noteNumbers[noteCount] = (byte) note;
                noteCount++;
            }
        } else if ((kind == 0xC0 || kind == 0xD0) && firstData < 0) {
            readByte();
        }
    }

    private void convertTicksToMillis() {
        for (int i = 0; i < noteCount; i++) {
            noteMillis[i] = ticksToMillis(noteMillis[i]);
        }
    }

    private int ticksToMillis(int targetTick) {
        long micros = 0;
        int previousTick = 0;
        int microsPerQuarter = DEFAULT_MICROS_PER_QUARTER;
        for (int i = 0; i < tempoCount && tempoTicks[i] <= targetTick; i++) {
            micros += (long) (tempoTicks[i] - previousTick) * microsPerQuarter;
            previousTick = tempoTicks[i];
            microsPerQuarter = tempoMicros[i];
        }
        micros += (long) (targetTick - previousTick) * microsPerQuarter;
        return (int) (micros / ((long) division * 1000L));
    }

    /** Whether the chunk header in the buffer is {@code MT} followed by {@code third} and {@code fourth}. */
    private boolean hasTag(char third, char fourth) {
        return buffer[0] == 'M' && buffer[1] == 'T' && buffer[2] == third && buffer[3] == fourth;
    }

    private int uint16At(int offset) {
        return (buffer[offset] & 0xFF) << 8 | buffer[offset + 1] & 0xFF;
    }

    private int int32At(int offset) {
        return (buffer[offset] & 0xFF) << 24 | (buffer[offset + 1] & 0xFF) << 16 | (buffer[offset + 2] & 0xFF) << 8
                | buffer[offset + 3] & 0xFF;
    }

    private int readVarLen() {
        int value = 0;
        int b;
        do {
            b = readByte();
            value = (value << 7) | (b & 0x7F);
        } while ((b & 0x80) != 0 && cursor < end);
        return value;
    }

    /** The next byte of the track, or {@code 0} past its end so a malformed track cannot read outside it. */
    private int readByte() {
        int b = cursor < end ? buffer[cursor] & 0xFF : 0;
        cursor++;
        return b;
    }
}
