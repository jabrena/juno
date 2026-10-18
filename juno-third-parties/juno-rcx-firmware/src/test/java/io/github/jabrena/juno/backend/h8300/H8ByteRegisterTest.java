package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class H8ByteRegisterTest {

    @Test
    void eachGeneralRegisterHasItsHighAndLowByte() {
        assertThat(H8ByteRegister.high(H8Register.R0)).isEqualTo(H8ByteRegister.R0H);
        assertThat(H8ByteRegister.high(H8Register.R1)).isEqualTo(H8ByteRegister.R1H);
        assertThat(H8ByteRegister.high(H8Register.R2)).isEqualTo(H8ByteRegister.R2H);
        assertThat(H8ByteRegister.high(H8Register.R3)).isEqualTo(H8ByteRegister.R3H);
        assertThat(H8ByteRegister.high(H8Register.R4)).isEqualTo(H8ByteRegister.R4H);
        assertThat(H8ByteRegister.high(H8Register.R5)).isEqualTo(H8ByteRegister.R5H);
        assertThat(H8ByteRegister.high(H8Register.R6)).isEqualTo(H8ByteRegister.R6H);
        assertThat(H8ByteRegister.high(H8Register.R7)).isEqualTo(H8ByteRegister.R7H);

        assertThat(H8ByteRegister.low(H8Register.R0)).isEqualTo(H8ByteRegister.R0L);
        assertThat(H8ByteRegister.low(H8Register.R1)).isEqualTo(H8ByteRegister.R1L);
        assertThat(H8ByteRegister.low(H8Register.R2)).isEqualTo(H8ByteRegister.R2L);
        assertThat(H8ByteRegister.low(H8Register.R3)).isEqualTo(H8ByteRegister.R3L);
        assertThat(H8ByteRegister.low(H8Register.R4)).isEqualTo(H8ByteRegister.R4L);
        assertThat(H8ByteRegister.low(H8Register.R5)).isEqualTo(H8ByteRegister.R5L);
        assertThat(H8ByteRegister.low(H8Register.R6)).isEqualTo(H8ByteRegister.R6L);
        assertThat(H8ByteRegister.low(H8Register.R7)).isEqualTo(H8ByteRegister.R7L);
    }

    @Test
    void highBytesAreNumberedFirstThenLowBytes() {
        assertThat(H8ByteRegister.high(H8Register.R0).number()).isZero();
        assertThat(H8ByteRegister.high(H8Register.R7).number()).isEqualTo(7);
        assertThat(H8ByteRegister.low(H8Register.R0).number()).isEqualTo(8);
        assertThat(H8ByteRegister.low(H8Register.R7).number()).isEqualTo(15);
    }
}
