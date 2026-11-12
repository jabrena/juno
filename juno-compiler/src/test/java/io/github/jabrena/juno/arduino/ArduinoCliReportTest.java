package io.github.jabrena.juno.arduino;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("arduino-cli output used by the core-drift check")
class ArduinoCliReportTest {

    private static final String COMPILE = """
            Sketch uses 1454 bytes (4%) of program storage space. Maximum is 32256 bytes.
            Global variables use 198 bytes (9%) of dynamic memory, leaving 1850 bytes for local variables. Maximum is 2048 bytes.
            """;

    private static final String CORE_LIST = """
            ID                  Installed        Latest           Name
            arduino:avr         1.8.8            1.8.8            Arduino AVR Boards
            arduino:renesas_uno 1.6.0            1.6.0            Arduino UNO R4 Boards
            esp32:esp32         3.3.12           3.3.12           esp32
            """;

    @Test
    @DisplayName("Reads the static RAM from the Global variables line")
    void shouldReadTheStaticRam() {
        assertThat(ArduinoCliReport.staticRamBytes(COMPILE)).isEqualTo(198);
    }

    @Test
    @DisplayName("Rejects output without a Global variables line")
    void shouldRejectOutputWithoutTheLine() {
        assertThatThrownBy(() -> ArduinoCliReport.staticRamBytes("Sketch uses 1454 bytes."))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Global variables");
    }

    @Test
    @DisplayName("Reads the installed version of a platform from core list")
    void shouldReadTheInstalledVersion() {
        assertThat(ArduinoCliReport.installedVersion(CORE_LIST, "arduino:renesas_uno")).isEqualTo("1.6.0");
        assertThat(ArduinoCliReport.installedVersion(CORE_LIST, "esp32:esp32")).isEqualTo("3.3.12");
    }

    @Test
    @DisplayName("Rejects a platform that core list does not show")
    void shouldRejectAnUninstalledPlatform() {
        assertThatThrownBy(() -> ArduinoCliReport.installedVersion(CORE_LIST, "arduino:zephyr"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Takes the platform from an FQBN, board options included")
    void shouldTakeThePlatformFromAnFqbn() {
        assertThat(ArduinoCliReport.platformOf("m5stack:esp32:m5stack_cardputer:USBMode=default"))
                .isEqualTo("m5stack:esp32");
        assertThat(ArduinoCliReport.platformOf("arduino:avr:uno")).isEqualTo("arduino:avr");
    }
}
