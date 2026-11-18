package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.backend.runtime.spi.ShimFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Shim feature blocks carry bundled source only for programs that use the feature")
class ShimFeatureBlockTest {

    @Test
    @DisplayName("A bundled block is appended only when its feature is used")
    void shouldAppendBundledSourceOnlyWhenTheFeatureIsUsed() {
        // Given
        List<ShimFeatureBlock> blocks = List.of(
                new ShimFeatureBlock(ShimFeature.CAMERA, () -> "// bundled camera source\n"),
                new ShimFeatureBlock(ShimFeature.PSRAM, () -> "// bundled psram source\n"));
        StringBuilder withoutCamera = new StringBuilder();
        StringBuilder withCamera = new StringBuilder();

        // When
        ShimFeatureBlock.appendUsed(withoutCamera, Set.of(ShimFeature.PSRAM), blocks);
        ShimFeatureBlock.appendUsed(withCamera, Set.of(ShimFeature.CAMERA), blocks);

        // Then
        assertThat(withoutCamera.toString()).isEqualTo("// bundled psram source\n");
        assertThat(withCamera.toString()).isEqualTo("// bundled camera source\n");
    }

    @Test
    @DisplayName("A program that uses none of the features gets no bundled source")
    void shouldAppendNothingWhenNoFeatureIsUsed() {
        // Given
        List<ShimFeatureBlock> blocks = List.of(
                new ShimFeatureBlock(ShimFeature.CAMERA, () -> "// bundled camera source\n"));
        StringBuilder shim = new StringBuilder();

        // When
        ShimFeatureBlock.appendUsed(shim, Set.of(), blocks);

        // Then
        assertThat(shim.toString()).isEmpty();
    }
}
