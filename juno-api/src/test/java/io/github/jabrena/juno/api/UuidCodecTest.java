package io.github.jabrena.juno.api;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidCodecTest {
    @Test
    void convertsUuidToAndFromNetworkByteOrderAtAnOffset() {
        UUID value = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        byte[] bytes = new byte[20];

        UuidCodec.toBytes(value, bytes, 2);

        assertThat(bytes).containsExactly(
                0, 0,
                0x12, 0x3e, 0x45, 0x67, (byte) 0xe8, (byte) 0x9b, 0x12, (byte) 0xd3,
                (byte) 0xa4, 0x56, 0x42, 0x66, 0x14, 0x17, 0x40, 0x00,
                0, 0);
        assertThat(UuidCodec.fromBytes(bytes, 2)).isEqualTo(value);
    }
}
