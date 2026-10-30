package io.github.jabrena.juno.usecases.zeldamidircx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * Small command-line inspector for Standard MIDI Files (SMF).
 */
public final class MidiParser {
    private static byte[] data;
    private static int cursor;
    private static int absTick;
    private static TrackInfo info;
    private static ArrayList<Integer> tempoTicks;
    private static ArrayList<Integer> tempoUs;

    private MidiParser() {
    }

    public static void main(String[] args) throws IOException {
        byte[] data;
        String source;
        if (args.length > 0) {
            Path path = Paths.get(args[0]);
            data = Files.readAllBytes(path);
            source = path.toString();
        } else {
            try (InputStream in = MidiParser.class.getResourceAsStream("/midi/Ocarina_of_Time_Medley.mid")) {
                if (in == null) {
                    throw new IOException("Resource /midi/Ocarina_of_Time_Medley.mid not found on the classpath");
                }
                data = readAll(in);
            }
            source = "/midi/Ocarina_of_Time_Medley.mid (classpath)";
        }
        System.out.println("Source: " + source);
        System.out.println("File size: " + data.length + " bytes");
        parse(data);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static void parse(byte[] data) {
        if (data.length < 14 || !ascii(data, 0, 4).equals("MThd")) {
            throw new IllegalArgumentException("Not a Standard MIDI File: missing MThd header");
        }
        int headerLen = readInt32(data, 4);
        int format = readUInt16(data, 8);
        int ntracks = readUInt16(data, 10);
        int division = readUInt16(data, 12);

        System.out.println("Format: " + format);
        System.out.println("Number of tracks: " + ntracks);
        System.out.println("Division (ticks per quarter note): " + division);

        int cursor = 8 + headerLen;
        TrackInfo[] tracks = new TrackInfo[ntracks];
        for (int tr = 0; tr < ntracks; tr++) {
            if (cursor + 8 > data.length || !ascii(data, cursor, 4).equals("MTrk")) {
                throw new IllegalArgumentException("Expected MTrk at track " + tr);
            }
            int trackLen = readInt32(data, cursor + 4);
            int trackStart = cursor + 8;
            int trackEnd = trackStart + trackLen;
            tracks[tr] = parseTrack(data, trackStart, trackEnd);
            cursor = trackEnd;
        }

        System.out.println();
        System.out.println("Tracks:");
        int lastTick = 0;
        for (int tr = 0; tr < ntracks; tr++) {
            TrackInfo t = tracks[tr];
            if (t.lastTick > lastTick) {
                lastTick = t.lastTick;
            }
            System.out.println("  Track " + tr + ": name=" + (t.name == null ? "(none)" : "'" + t.name + "'")
                    + ", noteOn=" + t.noteCount
                    + ", firstTick=" + t.firstTick
                    + ", lastTick=" + t.lastTick);
        }

        int[] tempoTicks;
        int[] tempoUs;
        int tempoCount;
        if (ntracks > 0 && tracks[0].tempoTicks != null) {
            tempoTicks = tracks[0].tempoTicks;
            tempoUs = tracks[0].tempoUs;
            tempoCount = tempoTicks.length;
        } else {
            tempoTicks = new int[0];
            tempoUs = new int[0];
            tempoCount = 0;
        }

        System.out.println();
        System.out.println("Tempo segments:");
        long totalMs = 0;
        int prev = 0;
        int uspq = 500000;
        int ti = 0;
        while (ti < tempoCount && tempoTicks[ti] <= 0) {
            uspq = tempoUs[ti];
            ti++;
        }
        while (prev < lastTick) {
            int nextTempoTick = (ti < tempoCount) ? tempoTicks[ti] : lastTick;
            int segmentEnd = (nextTempoTick > prev) ? Math.min(nextTempoTick, lastTick) : lastTick;
            if (segmentEnd <= prev) {
                if (ti < tempoCount && tempoTicks[ti] <= prev) {
                    uspq = tempoUs[ti];
                    ti++;
                    continue;
                }
                break;
            }
            long segMs = (long) (segmentEnd - prev) * uspq / ((long) division * 1000L);
            totalMs += segMs;
            System.out.printf("  tick %d -> %d: %.2f s (uspq %d)%n", prev, segmentEnd, segMs / 1000.0, uspq);
            prev = segmentEnd;
            while (ti < tempoCount && tempoTicks[ti] <= prev) {
                uspq = tempoUs[ti];
                ti++;
            }
        }
        if (prev < lastTick) {
            long segMs = (long) (lastTick - prev) * uspq / ((long) division * 1000L);
            totalMs += segMs;
            System.out.printf("  tick %d -> %d: %.2f s (uspq %d)%n", prev, lastTick, segMs / 1000.0, uspq);
            prev = lastTick;
        }

        System.out.println();
        System.out.printf("Total duration ~ %.1f s%n", totalMs / 1000.0);
    }
    private static TrackInfo parseTrack(byte[] data, int start, int end) {
        MidiParser.data = data;
        cursor = start;
        absTick = 0;
        info = new TrackInfo();
        tempoTicks = new ArrayList<>();
        tempoUs = new ArrayList<>();
        int runningStatus = 0;

        while (cursor < end) {
            int delta = 0;
            while (true) {
                if (cursor >= end) {
                    break;
                }
                int b = data[cursor] & 0xFF;
                cursor++;
                delta = (delta << 7) | (b & 0x7F);
                if ((b & 0x80) == 0) {
                    break;
                }
            }
            absTick += delta;
            if (cursor >= end) {
                break;
            }
            int b = data[cursor] & 0xFF;
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
            handleMidiEvent(status, end);
        }

        if (info.noteCount == 0) {
            info.firstTick = 0;
        }

        info.tempoTicks = new int[tempoTicks.size()];
        info.tempoUs = new int[tempoUs.size()];
        for (int i = 0; i < tempoTicks.size(); i++) {
            info.tempoTicks[i] = tempoTicks.get(i);
            info.tempoUs[i] = tempoUs.get(i);
        }
        return info;
    }

    private static void handleMidiEvent(int status, int end) {
        int hi = status & 0xF0;
        if (status == 0xFF) {
            int mtype = data[cursor] & 0xFF;
            cursor++;
            int mlen = readVarLen(data, cursor);
            cursor = skipVarLen(data, cursor);
            int payloadStart = cursor;
            if (mtype == 0x03) {
                info.name = new String(data, payloadStart, mlen, java.nio.charset.StandardCharsets.ISO_8859_1);
            } else if (mtype == 0x51 && mlen == 3) {
                int uspq = ((data[payloadStart] & 0xFF) << 16)
                        | ((data[payloadStart + 1] & 0xFF) << 8)
                        | (data[payloadStart + 2] & 0xFF);
                tempoTicks.add(absTick);
                tempoUs.add(uspq);
            }
            cursor = payloadStart + mlen;
        } else if (status == 0xF0 || status == 0xF7) {
            int slen = readVarLen(data, cursor);
            cursor = skipVarLen(data, cursor) + slen;
        } else if (hi == 0x80 || hi == 0x90 || hi == 0xA0 || hi == 0xB0 || hi == 0xE0) {
            int d1 = data[cursor] & 0xFF;
            cursor++;
            int d2 = data[cursor] & 0xFF;
            cursor++;
            if (hi == 0x90 && d2 > 0) {
                info.noteCount++;
                if (info.noteCount == 1) {
                    info.firstTick = absTick;
                }
                info.lastTick = absTick;
            }
        } else if (hi == 0xC0 || hi == 0xD0) {
            cursor++;
        }
    }

    private static int readUInt16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int readInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static int readVarLen(byte[] data, int offset) {
        int val = 0;
        int i = offset;
        while (true) {
            int b = data[i] & 0xFF;
            i++;
            val = (val << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) {
                break;
            }
        }
        return val;
    }

    private static int skipVarLen(byte[] data, int offset) {
        int i = offset;
        while (true) {
            int b = data[i] & 0xFF;
            i++;
            if ((b & 0x80) == 0) {
                break;
            }
        }
        return i;
    }

    private static String ascii(byte[] data, int offset, int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) (data[offset + i] & 0xFF));
        }
        return sb.toString();
    }

    private static final class TrackInfo {
        String name;
        int noteCount;
        int firstTick;
        int lastTick;
        int[] tempoTicks;
        int[] tempoUs;
    }
}
