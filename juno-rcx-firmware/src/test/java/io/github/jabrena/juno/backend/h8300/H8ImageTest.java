package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class H8ImageTest {

    @Test
    void writesHeaderDataAndEntryRecordsWithChecksums() {
        byte[] bytes = new byte[18];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }

        String srecords = new H8Image(0x8000, 0x8000, bytes).srecords("hi");

        assertThat(srecords.lines()).containsExactly(
                "S0050000686929",
                "S1138000000102030405060708090A0B0C0D0E0FF4",
                "S1058010101149",
                "S90380007C");
    }

    @Test
    void findsTextOnlyWhenItStartsBeforeTheLimit() {
        H8Image image = new H8Image(0x8000, 0x8000, "..knock".getBytes(StandardCharsets.US_ASCII));

        assertThat(image.containsBefore("knock", 0x8003)).isTrue();
        assertThat(image.containsBefore("knock", 0x8002)).isFalse();
        assertThat(image.containsBefore("byte", 0x9000)).isFalse();
    }
}
