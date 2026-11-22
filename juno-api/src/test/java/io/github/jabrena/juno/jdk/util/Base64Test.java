package io.github.jabrena.juno.jdk.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base64Test {
    @Test
    void decodesPemBodyWithMimeWhitespace() {
        String encoded = java.util.Base64.getMimeEncoder(8, new byte[]{'\n'})
                .encodeToString(new byte[]{0, 1, 2, 3, 42, 63, 64, 127, -1});
        Base64.Decoder decoder = Base64.getMimeDecoder();
        assertThat(Arrays.copyOf(decoder.decode(encoded), decoder.decodedLength()))
                .isEqualTo(java.util.Base64.getMimeDecoder().decode(encoded));
    }

    @Test
    void decodesPaddingAndEveryAlphabetRange() {
        for (String encoded : new String[]{"TQ==", "TWE=", "TWFu", "+/8=", "Zg==", "eg==", "MA=="}) {
            Base64.Decoder decoder = Base64.getMimeDecoder();
            assertThat(Arrays.copyOf(decoder.decode(encoded), decoder.decodedLength()))
                    .isEqualTo(java.util.Base64.getMimeDecoder().decode(encoded));
        }
    }

    @Test
    void rejectsInvalidLengthAndPadding() {
        assertThatThrownBy(() -> Base64.getMimeDecoder().decode("TQ=")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base64.getMimeDecoder().decode("T==="))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
