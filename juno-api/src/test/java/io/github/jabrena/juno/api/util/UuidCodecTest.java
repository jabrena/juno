package io.github.jabrena.juno.api.util;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
@DisplayName("UUID byte codec")
class UuidCodecTest {

    private static final UUID VALUE = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    @DisplayName("Writes a UUID in network byte order at an offset")
    void shouldWriteAUuidInNetworkByteOrderAtAnOffset() {
        // Given
        byte[] bytes = new byte[20];

        // When
        UuidCodec.toBytes(VALUE, bytes, 2);

        // Then
        assertThat(bytes).containsExactly(
                0, 0,
                0x12, 0x3e, 0x45, 0x67, (byte) 0xe8, (byte) 0x9b, 0x12, (byte) 0xd3,
                (byte) 0xa4, 0x56, 0x42, 0x66, 0x14, 0x17, 0x40, 0x00,
                0, 0);
    }

    @Test
    @DisplayName("Reads a UUID back from the same offset")
    void shouldReadAUuidBackFromTheSameOffset() {
        // Given
        byte[] bytes = new byte[20];
        UuidCodec.toBytes(VALUE, bytes, 2);

        // When
        UUID decoded = UuidCodec.fromBytes(bytes, 2);

        // Then
        assertThat(decoded).isEqualTo(VALUE);
    }
}
