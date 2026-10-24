package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("H8 image")
class H8ImageTest {

    @Test
    @DisplayName("Writes header, data and entry records with checksums")
    void shouldWriteHeaderDataAndEntryRecordsWithChecksums() {
        // Given
        byte[] bytes = new byte[18];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        H8Image image = new H8Image(0x8000, 0x8000, bytes);

        // When
        String srecords = image.srecords("hi");

        // Then
        assertThat(srecords.lines()).containsExactly(
                "S0050000686929",
                "S1138000000102030405060708090A0B0C0D0E0FF4",
                "S1058010101149",
                "S90380007C");
    }

    @Test
    @DisplayName("Finds text only when it starts before the limit")
    void shouldFindTextOnlyWhenItStartsBeforeTheLimit() {
        // Given
        H8Image image = new H8Image(0x8000, 0x8000, "..knock".getBytes(StandardCharsets.US_ASCII));

        // When & Then
        assertThat(image.containsBefore("knock", 0x8003)).isTrue();
        assertThat(image.containsBefore("knock", 0x8002)).isFalse();
        assertThat(image.containsBefore("byte", 0x9000)).isFalse();
    }

    @Test
    @DisplayName("Compares image byte contents in record methods")
    void shouldCompareImageByteContents() {
        // Given
        H8Image first = new H8Image(0x8000, 0x8000, new byte[] {1, 2, 3});
        H8Image second = new H8Image(0x8000, 0x8000, new byte[] {1, 2, 3});

        // When & Then
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first).hasToString("H8Image[origin=32768, entry=32768, bytes=[1, 2, 3]]");
    }
}
