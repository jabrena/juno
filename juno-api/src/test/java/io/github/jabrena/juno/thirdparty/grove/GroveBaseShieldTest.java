package io.github.jabrena.juno.thirdparty.grove;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Grove base shield socket mapping")
class GroveBaseShieldTest {

    @Test
    @DisplayName("Maps the Grove sockets to UNO pins")
    void shouldMapTheGroveSocketsToUnoPins() {
        GroveBaseShield shield = new GroveBaseShield();
        // When & Then
        assertThat(new int[] {
                shield.getA0(),
                shield.getD2(),
                shield.getD8(),
                shield.getUartRx(),
                shield.getI2cScl()})
            .containsExactly(14, 2, 8, 0, 19);
    }
}
