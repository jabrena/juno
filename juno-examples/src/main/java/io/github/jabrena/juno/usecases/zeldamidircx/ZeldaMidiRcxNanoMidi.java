package io.github.jabrena.juno.usecases.zeldamidircx;

public final class ZeldaMidiRcxNanoMidi {

    private static final int TRACK_1 = 1;
    private static final int TRACK_2 = 2;
    private static final int TRACK_3 = 3;

    private static final int MAX_TEMPO = 128;
    private static final int MAX_NOTES = 256;

    public static int division = 480;
    public static byte[] midiData;
    public static int cursor;

    public static final int[] tempoTick = new int[MAX_TEMPO];
    public static final int[] tempoUs = new int[MAX_TEMPO];
    public static int tempoCount = 0;

    public static final int[] noteTick1 = new int[MAX_NOTES];
    public static final int[] noteNum1 = new int[MAX_NOTES];
    public static int noteCount1 = 0;

    public static final int[] noteTick2 = new int[MAX_NOTES];
    public static final int[] noteNum2 = new int[MAX_NOTES];
    public static int noteCount2 = 0;

    public static final int[] noteTick3 = new int[MAX_NOTES];
    public static final int[] noteNum3 = new int[MAX_NOTES];
    public static int noteCount3 = 0;

    private ZeldaMidiRcxNanoMidi() {
    }

    public static void parseMidi() {
        cursor = 0;
        expect('M');
        expect('T');
        expect('h');
        expect('d');
        int headerLen = readInt32();
        int format = readUInt16();
        int ntracks = readUInt16();
        division = readUInt16();
        for (int k = 6; k < headerLen; k++) {
            readByte();
        }
        for (int tr = 0; tr < ntracks; tr++) {
            expect('M');
            expect('T');
            expect('r');
            expect('k');
            int trackLen = readInt32();
            int trackEnd = cursor + trackLen;
            parseTrack(tr, trackEnd);
            cursor = trackEnd;
        }
    }
    private static void parseTrack(int trackIndex, int trackEnd) {
        int absTick = 0;
        int runningStatus = 0;
        while (cursor < trackEnd) {
            int delta = 0;
            while (true) {
                int b = midiData[cursor] & 0xFF;
                cursor++;
                delta = (delta << 7) | (b & 0x7F);
                if ((b & 0x80) == 0) {
                    break;
                }
            }
            absTick += delta;
            if (cursor >= trackEnd) {
                break;
            }
            int b = midiData[cursor] & 0xFF;
            int status;
            if (b >= 0x80) {
                status = b;
                cursor++;
                if (status < 0xF0) {
                    runningStatus = status;
                }
            } else {
                status = runningStatus;
            }
            int hi = status & 0xF0;
            if (status == 0xFF) {
                int mtype = midiData[cursor] & 0xFF;
                cursor++;
                int mlen = readVarLen();
                if (mtype == 0x51 && mlen == 3) {
                    int uspq = ((midiData[cursor] & 0xFF) << 16) | ((midiData[cursor + 1] & 0xFF) << 8) | (midiData[cursor + 2] & 0xFF);
                    if (tempoCount < MAX_TEMPO) {
                        tempoTick[tempoCount] = absTick;
                        tempoUs[tempoCount] = uspq;
                        tempoCount++;
                    }
                } else if (mtype == 0x2F) {
                    cursor += mlen;
                    break;
                }
                cursor += mlen;
            } else if (status == 0xF0 || status == 0xF7) {
                int slen = readVarLen();
                cursor += slen;
            } else if (hi == 0x80 || hi == 0x90 || hi == 0xA0 || hi == 0xB0 || hi == 0xE0) {
                int d1 = midiData[cursor] & 0xFF;
                cursor++;
                int d2 = midiData[cursor] & 0xFF;
                cursor++;
                if (hi == 0x90 && d2 > 0) {
                    if (trackIndex == TRACK_1 && noteCount1 < MAX_NOTES) {
                        noteTick1[noteCount1] = absTick;
                        noteNum1[noteCount1] = d1;
                        noteCount1++;
                    } else if (trackIndex == TRACK_2 && noteCount2 < MAX_NOTES) {
                        noteTick2[noteCount2] = absTick;
                        noteNum2[noteCount2] = d1;
                        noteCount2++;
                    } else if (trackIndex == TRACK_3 && noteCount3 < MAX_NOTES) {
                        noteTick3[noteCount3] = absTick;
                        noteNum3[noteCount3] = d1;
                        noteCount3++;
                    }
                }
            } else if (hi == 0xC0 || hi == 0xD0) {
                cursor++;
            }
        }
    }

    private static int readVarLen() {
        int val = 0;
        while (true) {
            int b = midiData[cursor] & 0xFF;
            cursor++;
            val = (val << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) {
                break;
            }
        }
        return val;
    }

    private static int readByte() {
        int b = midiData[cursor] & 0xFF;
        cursor++;
        return b;
    }

    private static int readUInt16() {
        int hi = readByte();
        int lo = readByte();
        return (hi << 8) | lo;
    }

    private static int readInt32() {
        int b1 = readByte();
        int b2 = readByte();
        int b3 = readByte();
        int b4 = readByte();
        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }

    private static void expect(char c) {
        readByte();
    }

    public static int ticksToMillis(int targetTick) {
        int result = 0;
        int prev = 0;
        int uspq = 500000;
        int i = 0;
        while (i < tempoCount && tempoTick[i] <= targetTick) {
            int segEnd = tempoTick[i];
            if (segEnd > prev) {
                result += (int) ((long) (segEnd - prev) * uspq / ((long) division * 1000L));
            }
            prev = segEnd;
            uspq = tempoUs[i];
            i++;
        }
        if (targetTick > prev) {
            result += (int) ((long) (targetTick - prev) * uspq / ((long) division * 1000L));
        }
        return result;
    }

    public static int durationCentiseconds(int toneMillis) {
        int centiseconds = toneMillis / 10;
        if (centiseconds < 1) {
            return 1;
        }
        return centiseconds > 255 ? 255 : centiseconds;
    }

    public static int noteCount() {
        return noteCount1 + noteCount2 + noteCount3;
    }
}
