package io.github.jabrena.juno.backend.h8300;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
@DisplayName("H8 byte registers")
class H8ByteRegisterTest {

    @ParameterizedTest(name = "{0} has high byte {1} and low byte {2}")
    @CsvSource({
            "R0, R0H, R0L",
            "R1, R1H, R1L",
            "R2, R2H, R2L",
            "R3, R3H, R3L",
            "R4, R4H, R4L",
            "R5, R5H, R5L",
            "R6, R6H, R6L",
            "R7, R7H, R7L"
    })
    @DisplayName("Gives each general register its high and low byte")
    void shouldGiveEachGeneralRegisterItsHighAndLowByte(H8Register register, H8ByteRegister high,
            H8ByteRegister low) {
        // When & Then
        assertThat(H8ByteRegister.high(register)).isEqualTo(high);
        assertThat(H8ByteRegister.low(register)).isEqualTo(low);
    }

    @Test
    @DisplayName("Numbers the high bytes first, then the low bytes")
    void shouldNumberTheHighBytesFirstThenTheLowBytes() {
        // When & Then
        assertThat(H8ByteRegister.high(H8Register.R0).number()).isZero();
        assertThat(H8ByteRegister.high(H8Register.R7).number()).isEqualTo(7);
        assertThat(H8ByteRegister.low(H8Register.R0).number()).isEqualTo(8);
        assertThat(H8ByteRegister.low(H8Register.R7).number()).isEqualTo(15);
    }
}
