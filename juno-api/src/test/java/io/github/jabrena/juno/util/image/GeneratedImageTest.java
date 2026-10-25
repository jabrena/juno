package io.github.jabrena.juno.util.image;

import io.github.jabrena.juno.hardware.device.tft.ParallelBus;
import io.github.jabrena.juno.hardware.device.tft.TftPins;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.testing.ImmediateDelay;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

@DisplayName("Generated TFT images")
class GeneratedImageTest {
    @BeforeEach
    void initialiseTheDisplay() {
        Wire.resetBoard();
        TftPins.use(mock(Gpio.class));
        ParallelBus.reset();
        ImmediateDelay.run(TftTouchShield::begin);
        ParallelBus.bytes.clear();
        ParallelBus.runs.clear();
    }

    static Stream<Arguments> images() {
        return Stream.of(
                arguments("Java logo", new JavaLogoImage(), JavaLogoImage.WIDTH, JavaLogoImage.HEIGHT, 5_872),
                arguments(
                        "Arduino UNO board",
                        new ArduinoUnoBoardImage(),
                        ArduinoUnoBoardImage.WIDTH,
                        ArduinoUnoBoardImage.HEIGHT,
                        46_190),
                arguments(
                        "M5Stack Cardputer",
                        new M5StackCardputerImage(),
                        M5StackCardputerImage.WIDTH,
                        M5StackCardputerImage.HEIGHT,
                        60_796),
                arguments(
                        "Arduino Nano ESP32",
                        new ArduinoNanoEsp32Image(),
                        ArduinoNanoEsp32Image.WIDTH,
                        ArduinoNanoEsp32Image.HEIGHT,
                        35_515));
    }

    @ParameterizedTest(name = "{0} renders {2}x{3} with {4} visible pixels")
    @MethodSource("images")
    @DisplayName("Draws each generated image through the TFT backend")
    void shouldDrawTheGeneratedImage(
            String name, Drawable image, int width, int height, int expectedVisiblePixels) {
        // When
        image.draw();

        // Then
        assertThat(image.getWidth()).isEqualTo(width);
        assertThat(image.getHeight()).isEqualTo(height);
        assertThat(width).isLessThanOrEqualTo(TftTouchShield.width());
        assertThat(height).isLessThanOrEqualTo(TftTouchShield.height());
        assertThat(ParallelBus.bytes).isNotEmpty();
        assertThat(ParallelBus.runs)
                .as(name + " should produce visible pixel runs")
                .isNotEmpty()
                .allSatisfy(run -> assertThat(run[1]).isPositive());
        assertThat(ParallelBus.runs.stream().mapToInt(run -> run[1]).sum())
                .as(name + " visible pixel count")
                .isEqualTo(expectedVisiblePixels);
    }

    @ParameterizedTest(name = "{0} keeps every encoded run inside one row")
    @MethodSource("images")
    @DisplayName("Keeps each generated RLE run inside its source row")
    void shouldKeepEveryRunInsideItsSourceRow(
            String name, Drawable image, int width, int height, int expectedVisiblePixels)
            throws ReflectiveOperationException {
        var lengthField = image.getClass().getDeclaredField("LENGTH");
        lengthField.setAccessible(true);
        int encodedLength = lengthField.getInt(null);
        var byteAt = image.getClass().getDeclaredMethod("byteAt", int.class);
        byteAt.setAccessible(true);

        int x = 0;
        int rows = 0;
        for (int offset = 0; offset < encodedLength; offset += 2) {
            int runLength = (int) byteAt.invoke(null, offset + 1);
            assertThat(x + runLength)
                    .as(name + " run at byte " + offset)
                    .isLessThanOrEqualTo(width);
            x += runLength;
            if (x == width) {
                x = 0;
                rows++;
            }
        }
        assertThat(x).isZero();
        assertThat(rows).isEqualTo(height);
    }
}
