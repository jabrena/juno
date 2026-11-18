package io.github.jabrena.juno.thirdparty.cat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Cat detector training engine")
class CatDetectorTrainingTest {

    @Test
    @DisplayName("The DJL engine starts and allocates a 64x64 grayscale frame")
    void shouldStartTheEngineAndAllocateAFrame() {
        assertThat(CatDetectorTraining.smokeCheck()).isEqualTo(64L * 64L);
    }
}
