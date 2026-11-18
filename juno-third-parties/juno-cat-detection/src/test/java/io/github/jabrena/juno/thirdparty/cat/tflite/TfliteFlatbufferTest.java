package io.github.jabrena.juno.thirdparty.cat.tflite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Hand-written TFLite FlatBuffer writer")
class TfliteFlatbufferTest {

    @Test
    @DisplayName("The Model version field round-trips through the raw FlatBuffer layout")
    void shouldRoundTripTheModelVersion() {
        // Given
        byte[] bytes = TfliteFlatbuffer.modelWithVersion(TfliteFlatbuffer.SCHEMA_VERSION);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        // When: the root offset at the start points at the Model table; its first int32 is the offset back to the vtable.
        int table = buffer.getInt(0);
        int vtable = table - buffer.getInt(table);
        int versionSlot = buffer.getShort(vtable + 4 + 2 * TfliteFlatbuffer.MODEL_VERSION) & 0xFFFF;

        // Then
        assertThat(versionSlot).as("vtable slot for the version field").isNotZero();
        assertThat(buffer.getInt(table + versionSlot)).isEqualTo(TfliteFlatbuffer.SCHEMA_VERSION);
    }
}
