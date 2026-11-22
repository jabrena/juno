package io.github.jabrena.juno.hardware.device.camera;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("Camera frame sizes")
class FrameSizeTest {

    @Test
    @DisplayName("Lists the sizes smallest first, in the order the native driver table expects")
    void shouldListTheSizesSmallestFirst() {
        // When & Then
        assertThat(FrameSize.values()).containsExactly(
                FrameSize.QQVGA,
                FrameSize.QVGA,
                FrameSize.VGA,
                FrameSize.SVGA);
    }

    @Test
    @DisplayName("Reports each size's width and height in pixels")
    void shouldReportEachSizeInPixels() {
        // When & Then
        assertThat(FrameSize.values())
                .extracting(FrameSize::width, FrameSize::height)
                .containsExactly(
                        tuple(160, 120),
                        tuple(320, 240),
                        tuple(640, 480),
                        tuple(800, 600));
    }
}
