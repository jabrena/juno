package io.github.jabrena.juno.backend.h8300;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Assembled H8/300 machine code: {@code bytes} placed from {@code origin}, entered at {@code entry}.
 *
 * @param origin the address of the first byte
 * @param entry the address execution starts at
 * @param bytes the machine code and data
 */
public record H8Image(int origin, int entry, byte[] bytes) {
    private static final int RECORD_DATA = 16;

    public H8Image {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    /** Whether {@code text} appears in the image as ASCII and starts before {@code limit}. */
    public boolean containsBefore(String text, int limit) {
        byte[] wanted = text.getBytes(StandardCharsets.US_ASCII);
        for (int at = 0; at + wanted.length <= bytes.length && origin + at < limit; at++) {
            if (Arrays.equals(bytes, at, at + wanted.length, wanted, 0, wanted.length)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The image as Motorola S-records: an {@code S0} header holding {@code name}, {@code S1} data records of up to 16
     * bytes and an {@code S9} record holding the entry address, one per line.
     */
    public String srecords(String name) {
        StringBuilder text = new StringBuilder();
        record(text, 0, 0, name.getBytes(StandardCharsets.US_ASCII));
        for (int at = 0; at < bytes.length; at += RECORD_DATA) {
            record(text, 1, origin + at, Arrays.copyOfRange(bytes, at, Math.min(bytes.length, at + RECORD_DATA)));
        }
        record(text, 9, entry, new byte[0]);
        return text.toString();
    }

    /** Writes {@link #srecords} to {@code output} and reports the image's size on standard output. */
    public void writeSrecords(Path output, String name) throws IOException {
        Files.writeString(output, srecords(name), StandardCharsets.US_ASCII);
        System.out.printf("Wrote %s: %d bytes, entry 0x%04X%n", output, bytes.length, entry);
    }

    private static void record(StringBuilder text, int type, int address, byte[] data) {
        int count = data.length + 3;
        int sum = count + (address >> 8 & 0xFF) + (address & 0xFF);
        text.append('S').append(type).append(hex(count)).append(hex(address >> 8)).append(hex(address));
        for (byte value : data) {
            sum += value & 0xFF;
            text.append(hex(value));
        }
        text.append(hex(~sum)).append('\n');
    }

    private static String hex(int value) {
        return "%02X".formatted(value & 0xFF);
    }
}
