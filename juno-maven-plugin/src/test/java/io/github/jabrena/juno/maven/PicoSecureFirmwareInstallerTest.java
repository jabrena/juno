package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.thirdparty.pico.secure.PicoSecureFirmware;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pico 2 W secure firmware installer")
class PicoSecureFirmwareInstallerTest {
    @Test
    @DisplayName("Rewrites the UF2's boot slot with the bundled firmware and keeps the sketch")
    void shouldInstallTheBundledFirmwareIntoTheBootSlot(@TempDir Path directory) throws IOException {
        // Given: a UF2 with the 48 slot pages and one sketch page after them
        Path uf2 = directory.resolve("SecretsAsm.ino.uf2");
        Files.write(uf2, PicoUf2.of(49));
        byte[] firmware = PicoSecureFirmware.bundled().image();

        // When
        new PicoSecureFirmwareInstaller().install(uf2);

        // Then
        byte[] installed = Files.readAllBytes(uf2);
        assertThat(PicoUf2.payload(installed, 0)).isEqualTo(Arrays.copyOf(firmware, 256));
        assertThat(PicoUf2.payload(installed, 48)).containsOnly((byte) 49);
    }

    @Test
    @DisplayName("Reports a file that is missing or not a UF2 as an Arduino CLI failure")
    void shouldReportAMissingOrBrokenUf2(@TempDir Path directory) throws IOException {
        Path missing = directory.resolve("missing.uf2");
        Path broken = directory.resolve("broken.uf2");
        Files.write(broken, new byte[100]);
        PicoSecureFirmwareInstaller installer = new PicoSecureFirmwareInstaller();

        assertThatThrownBy(() -> installer.install(missing)).isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("Could not install the Pico 2 W secure firmware into " + missing);
        assertThatThrownBy(() -> installer.install(broken)).isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("multiple of 512");
    }

    @Test
    @DisplayName("Reports firmware that cannot be loaded")
    void shouldReportMissingFirmware(@TempDir Path directory) throws IOException {
        Path uf2 = directory.resolve("SecretsAsm.ino.uf2");
        Files.write(uf2, PicoUf2.of(48));
        PicoSecureFirmwareInstaller installer = new PicoSecureFirmwareInstaller(() -> {
            throw new IOException("no image");
        });

        assertThatThrownBy(() -> installer.install(uf2)).isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("no image");
    }

    /** UF2 files as arduino-pico writes them: consecutive 256-byte pages from the start of flash. */
    static final class PicoUf2 {
        private PicoUf2() {
        }

        /** {@code pages} pages from {@code 0x10000000}, page {@code n} filled with {@code n + 1}. */
        static byte[] of(int pages) {
            ByteBuffer file = ByteBuffer.allocate(512 * pages).order(ByteOrder.LITTLE_ENDIAN);
            for (int page = 0; page < pages; page++) {
                int base = 512 * page;
                file.putInt(base, 0x0A324655).putInt(base + 4, 0x9E5D5157).putInt(base + 8, 0x2000)
                        .putInt(base + 12, 0x10000000 + 256 * page).putInt(base + 16, 256)
                        .putInt(base + 20, page).putInt(base + 24, pages).putInt(base + 28, 0xE48BFF59)
                        .putInt(base + 508, 0x0AB16F30);
                for (int i = 0; i < 256; i++) {
                    file.put(base + 32 + i, (byte) (page + 1));
                }
            }
            return file.array();
        }

        static byte[] payload(byte[] uf2, int block) {
            return Arrays.copyOfRange(uf2, 512 * block + 32, 512 * block + 288);
        }
    }
}
