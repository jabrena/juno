package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RCX firmware image source")
class RcxFirmwareImageSourceTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    @DisplayName("Generates the image metadata, bytes and checksum")
    void shouldGenerateImageMetadataBytesAndChecksum() throws Exception {
        // Given
        H8Image image = new H8Image(0x8000, 0x8001, new byte[] {1, 2, -1});
        RcxFirmwareImageSource generator = new RcxFirmwareImageSource(image);
        Path output = temporaryDirectory.resolve("generated/RcxFirmwareImage.java");

        // When
        generator.write(output);

        // Then
        assertThat(Files.readString(output))
                .contains("public final class RcxFirmwareImage")
                .contains("public static final int ORIGIN = 0x8000;")
                .contains("public static final int LENGTH = 3;")
                .contains("public static final int ENTRY = 0x8001;")
                .contains("public static final int CHECKSUM = 0x0102;")
                .contains("public static byte byteAt(int address)")
                .contains("private static final byte[] DATA = {\n                1, 2, -1,");
    }

    @Test
    @DisplayName("Splits large images into independent initializer classes")
    void shouldSplitLargeImagesIntoIndependentInitializerClasses() {
        // Given
        byte[] bytes = new byte[4097];

        // When
        String source = new RcxFirmwareImageSource(new H8Image(0x8000, 0x8000, bytes)).source();

        // Then
        assertThat(source).contains("case 0 -> Chunk0.DATA[offset];", "case 1 -> Chunk1.DATA[offset];")
                .contains("private static final class Chunk0", "private static final class Chunk1");
    }

    @Test
    @DisplayName("Rejects images the RCX cannot load")
    void shouldRejectImagesTheRcxCannotLoad() {
        // When & Then
        assertThatThrownBy(() -> new RcxFirmwareImageSource(new H8Image(0x7000, 0x7000, new byte[] {1})))
                .isInstanceOf(FirmwareGenerationException.class)
                .hasMessageContaining("start at 0x8000");
        assertThatThrownBy(() -> new RcxFirmwareImageSource(new H8Image(0x8000, 0x8000, new byte[0])))
                .isInstanceOf(FirmwareGenerationException.class)
                .hasMessageContaining("length");
        assertThatThrownBy(() -> new RcxFirmwareImageSource(new H8Image(0x8000, 0x8001, new byte[] {1})))
                .isInstanceOf(FirmwareGenerationException.class)
                .hasMessageContaining("entry is outside");
    }

    @Test
    @DisplayName("Rejects an image too long for the ROM area")
    void shouldRejectAnImageTooLongForTheRomArea() {
        // When & Then
        assertThatThrownBy(() -> new RcxFirmwareImageSource(new H8Image(0x8000, 0x8000, new byte[28_673])))
                .isInstanceOf(FirmwareGenerationException.class)
                .hasMessageContaining("28672");
        assertThatThrownBy(() -> new RcxFirmwareImageSource(new H8Image(0x8000, 0x7FFF, new byte[] {1})))
                .isInstanceOf(FirmwareGenerationException.class)
                .hasMessageContaining("entry is outside");
    }
}
