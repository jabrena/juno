package io.github.jabrena.juno.hardware.device.ti;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class TiRealTest {
    private final TiReal real = new TiReal();

    @ParameterizedTest(name = "{0} with {1} decimals is {2}")
    @CsvSource({
        "0, 0, 008000000000000000",
        "23, 0, 008123000000000000",
        "235, 1, 008123500000000000",
        "-5, 0, 808050000000000000",
        "5, 2, 007E50000000000000",
        "100, 0, 008210000000000000",
        "20261121170016, 0, 008D20261121170016",
        "123456789012345678, 0, 00911234567890123456"
    })
    void shouldEncodeTiReals(long value, int decimals, String expected) {
        byte[] target = new byte[TiReal.BYTES + 2];

        real.put(target, 1, value, decimals);

        String encoded = HexFormat.of().withUpperCase().formatHex(target, 1, 1 + TiReal.BYTES);
        assertThat(encoded).isEqualTo(expected.substring(0, TiReal.BYTES * 2));
        assertThat(target[0]).isZero();
        assertThat(target[TiReal.BYTES + 1]).isZero();
    }
}
