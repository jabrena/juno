package io.github.jabrena.juno.api.lego.rcxfirmware;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class RcxFirmwareImageTest {
    private static final int CHECKSUM_END = 0xCC00;

    @Test
    void generatedJavaImageMatchesTheTrackedSRecord() throws Exception {
        SRecordImage srecord = readSRecord();
        byte[] javaImage = new byte[RcxFirmwareImage.LENGTH];
        for (int address = 0; address < javaImage.length; address++) {
            javaImage[address] = RcxFirmwareImage.byteAt(address);
        }

        assertThat(javaImage).isEqualTo(srecord.bytes());
        assertThat(RcxFirmwareImage.ENTRY).isEqualTo(srecord.entry());
        assertThat(RcxFirmwareImage.CHECKSUM).isEqualTo(checksum(srecord.bytes()));
    }

    private static SRecordImage readSRecord() throws IOException {
        byte[] bytes = new byte[RcxFirmwareImage.LENGTH];
        int length = 0;
        int entry = -1;
        try (InputStream input = Objects.requireNonNull(
                        RcxFirmwareImageTest.class.getResourceAsStream("/rcx/firmware.srec"),
                        "missing /rcx/firmware.srec");
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int count = hexByte(line, 2);
                assertRecordChecksum(line, count);
                int type = line.charAt(1) - '0';
                int address = hexByte(line, 4) << 8 | hexByte(line, 6);
                if (type == 1) {
                    int dataLength = count - 3;
                    int offset = address - RcxFirmwareImage.ORIGIN;
                    for (int i = 0; i < dataLength; i++) {
                        bytes[offset + i] = (byte) hexByte(line, 8 + 2 * i);
                    }
                    length = Math.max(length, offset + dataLength);
                } else if (type == 9) {
                    entry = address;
                }
            }
        }
        assertThat(length).isEqualTo(RcxFirmwareImage.LENGTH);
        return new SRecordImage(bytes, entry);
    }

    private static void assertRecordChecksum(String line, int count) {
        assertThat(line).hasSize(4 + 2 * count);
        int sum = count;
        for (int i = 0; i < count; i++) {
            sum += hexByte(line, 4 + 2 * i);
        }
        assertThat(sum & 0xFF).as("S-record checksum for %s", line).isEqualTo(0xFF);
    }

    private static int checksum(byte[] bytes) {
        int covered = Math.min(bytes.length, CHECKSUM_END - RcxFirmwareImage.ORIGIN);
        int sum = 0;
        for (int i = 0; i < covered; i++) {
            sum += bytes[i] & 0xFF;
        }
        return sum & 0xFFFF;
    }

    private static int hexByte(String text, int at) {
        return Integer.parseInt(text.substring(at, at + 2), 16);
    }

    private record SRecordImage(byte[] bytes, int entry) {
    }
}
