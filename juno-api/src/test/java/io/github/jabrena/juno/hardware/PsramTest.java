package io.github.jabrena.juno.hardware;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Psram")
class PsramTest {
    @Test
    @DisplayName("The static API has a fixed bounded handle capacity")
    void shouldExposeTheHandleLimit() {
        assertThat(Psram.MAX_LIVE_HANDLES).isEqualTo(16);
        assertThat(Psram.OK).isZero();
        assertThat(Psram.ERROR_INVALID_HANDLE).isNegative();
    }

    @Test
    @DisplayName("The API class is final")
    void shouldBeFinalUtilityClass() {
        assertThat(Psram.class).isFinal();
    }
}
