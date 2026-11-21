package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("A board's dfu-util upload recipe, narrowed to one device")
class DfuUploadPatternTest {
    private static final String PORTENTA = """
            tools.dfu-util.cmd=dfu-util
            tools.dfu-util.upload.pattern="{path}/{cmd}" --device {upload.vid}:{upload.pid} -D "{build.path}/{build.project_name}.bin" -a{upload.interface} --dfuse-address={upload.address}:leave
            upload.tool=dfu-util
            upload.tool.default=dfu-util
            """;

    @Test
    @DisplayName("Keeps the core's own recipe, placeholders included, and appends the serial number")
    void shouldAppendTheSerialToTheCoresRecipe() {
        // When
        String property = DfuUploadPattern.from("arduino:mbed_portenta:envie_m7", PORTENTA)
                .selecting("001D00293230510F31303431");

        // Then
        assertThat(property).isEqualTo("upload.pattern=\"{path}/{cmd}\" --device {upload.vid}:"
                + "{upload.pid} -D \"{build.path}/{build.project_name}.bin\" -a{upload.interface} "
                + "--dfuse-address={upload.address}:leave -S \"001D00293230510F31303431\"");
    }

    @Test
    @DisplayName("Falls back to the default upload tool, as the Nano ESP32's properties name it")
    void shouldUseTheDefaultUploadTool() {
        // Given
        String nano = """
                tools.dfu-util.cmd=dfu-util
                tools.dfu-util.upload.pattern="{path}/{cmd}" --device {vid.0}:{pid.0} -D "{build.path}/{build.project_name}.bin" -Q
                upload.tool.default=dfu-util
                upload.tool.network=esp_ota
                """;

        // When & Then
        assertThat(DfuUploadPattern.from("arduino:esp32:nano_nora", nano).selecting("206EF1332EC4"))
                .startsWith("upload.pattern=").endsWith("-Q -S \"206EF1332EC4\"");
    }

    @Test
    @DisplayName("Refuses a board that does not upload with dfu-util")
    void shouldRefuseABoardWithoutDfuUtil() {
        // Given
        String unoR4 = """
                tools.bossac.cmd=bossac
                tools.bossac.upload.pattern="{path}/{cmd}" -d --port={serial.port.file} -U -i -e -w "{build.path}/{build.project_name}.bin" -R
                upload.tool=bossac
                """;

        // When & Then
        assertThatThrownBy(() -> DfuUploadPattern.from("arduino:renesas_uno:unor4wifi", unoR4))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("only to boards that upload with dfu-util")
                .hasMessageContaining("arduino:renesas_uno:unor4wifi uploads with bossac");
    }

    @Test
    @DisplayName("Refuses a serial number that could carry anything else into the command line")
    void shouldRefuseASerialThatIsNotOne() {
        // Given
        DfuUploadPattern pattern = DfuUploadPattern.from("arduino:mbed_portenta:envie_m7", PORTENTA);

        // When & Then
        assertThatThrownBy(() -> pattern.selecting("001D\" -D other.bin"))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("must be a USB serial number");
        assertThatThrownBy(() -> pattern.selecting("a b")).isInstanceOf(ArduinoCliException.class);
    }
}
