package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Arduino CLI wrapper")
class ArduinoCliTest {
    private static final String FQBN = Board.UNO_R4_WIFI.fqbn();

    @Nested
    @DisplayName("Compiling a sketch")
    class Compiling {
        @ParameterizedTest(name = "{0} sketch {1}")
        @MethodSource("boardsAndTheirBuildProperties")
        @DisplayName("Passes the expected build properties for each board")
        void shouldPassTheExpectedBuildPropertiesForEachBoard(String fqbn, String sketchName,
                List<String> buildProperties) {
            // Given
            FakeExecutor executor = new FakeExecutor().respond(0, "compiled");
            ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });
            Path sketch = Path.of("target/juno/" + sketchName);

            // When
            cli.compile(fqbn, sketch);

            // Then
            List<String> expected = new ArrayList<>(List.of("arduino-cli", "compile", "--fqbn", fqbn));
            expected.addAll(buildProperties);
            expected.add(sketch.toAbsolutePath().normalize().toString());
            assertThat(executor.invocations()).containsExactly(captured(expected));
        }

        @Test
        @DisplayName("Records the compiler output in the log")
        void shouldRecordTheCompilerOutputInTheLog() {
            // Given
            FakeExecutor executor = new FakeExecutor().respond(0, "compiled");
            List<String> output = new ArrayList<>();
            ArduinoCli cli = new ArduinoCli("arduino-cli", executor, output::add);

            // When
            cli.compile(FQBN, Path.of("target/juno/Blink"));

            // Then
            assertThat(output).containsExactly("compiled");
        }

        @Test
        @DisplayName("Includes the arduino-cli output when the command fails")
        void shouldIncludeTheArduinoCliOutputWhenTheCommandFails() {
            // Given
            FakeExecutor executor = new FakeExecutor().respond(2, "platform not installed");
            ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

            // When & Then
            assertThatThrownBy(() -> cli.compile(FQBN, Path.of("target/juno/Blink")))
                    .isInstanceOf(ArduinoCliException.class)
                    .hasMessageContaining("exit code 2")
                    .hasMessageContaining("platform not installed");
        }

        private static Stream<Arguments> boardsAndTheirBuildProperties() {
            return Stream.of(
                    Arguments.of(Board.UNO_R4_WIFI.fqbn(), "Blink", List.of()),
                    Arguments.of(Board.UNO_R3.fqbn(), "Blink", List.of()),
                    Arguments.of(Board.UNO_R3_WIFI_REV2.fqbn(), "WifiStatusTFT",
                            List.of("--build-property", "compiler.cpp.extra_flags=-DSPI_DRIVER_SELECT=3")),
                    Arguments.of(Board.UNO_Q.fqbn(), "DashboardTFT",
                            List.of("--build-property", "compiler.cpp.extra_flags=-DENABLE_ARDUINO_SERIAL=0",
                                    "--build-property", "compiler.c.extra_flags=-Dasm=__asm__",
                                    "--build-property", "compiler.c.elf.extra_flags=-Wl,--allow-multiple-definition")));
        }
    }

    @Nested
    @DisplayName("Installing cores and libraries")
    class Installing {
        @Test
        @DisplayName("Installs a core and a library with the expected arguments")
        void shouldInstallACoreAndALibraryWithTheExpectedArguments() {
            // Given
            FakeExecutor executor = new FakeExecutor()
                    .respond(0, "core installed")
                    .respond(0, "core installed")
                    .respond(0, "lib installed");
            List<String> output = new ArrayList<>();
            ArduinoCli cli = new ArduinoCli("arduino-cli", executor, output::add);

            // When
            cli.installCore("arduino:renesas_uno");
            cli.installCore("m5stack:esp32", "https://example.test/package_m5stack_index.json");
            cli.installLibrary("ESP_SSLClient");

            // Then
            assertThat(executor.invocations()).containsExactly(
                    captured(List.of("arduino-cli", "core", "install", "arduino:renesas_uno")),
                    captured(List.of("arduino-cli", "core", "install", "m5stack:esp32", "--additional-urls",
                            "https://example.test/package_m5stack_index.json")),
                    captured(List.of("arduino-cli", "lib", "install", "ESP_SSLClient")));
            assertThat(output).containsExactly("core installed", "core installed", "lib installed");
        }
    }

    @Nested
    @DisplayName("Resolving the serial port")
    class ResolvingThePort {
        @Test
        @DisplayName("Discovers the only port that matches the board")
        void shouldDiscoverTheOnlyPortThatMatchesTheBoard() {
            // Given
            FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                    detected("/dev/cu.Bluetooth", ""),
                    detected("/dev/cu.usbmodem123", FQBN)));
            ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

            // When
            String port = cli.resolvePort(FQBN, Optional.empty());

            // Then
            assertThat(port).isEqualTo("/dev/cu.usbmodem123");
            assertThat(executor.invocations()).containsExactly(
                    captured(List.of("arduino-cli", "board", "list", "--json")));
        }

        @Test
        @DisplayName("Discovers the Cardputer from its Espressif USB serial vendor ID")
        void shouldDiscoverTheCardputerFromItsEspressifUsbSerialVendorId() {
            // Given
            String m5Cardputer = "m5stack:esp32:m5stack_cardputer";
            FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                    detected("/dev/cu.Bluetooth", ""),
                    detectedUsbSerial("/dev/cu.usbmodem1201", "0x303A")));
            ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

            // When
            String port = cli.resolvePort(m5Cardputer, Optional.empty());

            // Then
            assertThat(port).isEqualTo("/dev/cu.usbmodem1201");
        }

        @Test
        @DisplayName("Validates a configured port against the board and its FQBN")
        void shouldValidateAConfiguredPortAgainstTheBoardAndItsFqbn() {
            // Given
            FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                    detected("COM3", FQBN), detected("COM4", Board.UNO_R3.fqbn())));
            ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

            // When
            String port = cli.resolvePort(FQBN, Optional.of(" COM3 "));

            // Then
            assertThat(port).isEqualTo("COM3");
        }

        @Test
        @DisplayName("Rejects a configured port that belongs to a different board")
        void shouldRejectAConfiguredPortForADifferentBoard() {
            // Given
            FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                    detected("COM3", FQBN), detected("COM4", Board.UNO_R3.fqbn())));
            ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

            // When & Then
            assertThatThrownBy(() -> cli.resolvePort(FQBN, Optional.of("COM4")))
                    .isInstanceOf(ArduinoCliException.class)
                    .hasMessageContaining("Configured port 'COM4'")
                    .hasMessageContaining("Matching ports: COM3");
        }

        @Test
        @DisplayName("Asks for a port when several matching boards are connected")
        void shouldAskForAPortWhenSeveralMatchingBoardsAreConnected() {
            // Given
            FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                    detected("COM3", FQBN), detected("COM4", FQBN)));
            ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

            // When & Then
            assertThatThrownBy(() -> cli.resolvePort(FQBN, Optional.empty()))
                    .isInstanceOf(ArduinoCliException.class)
                    .hasMessageContaining("Multiple boards")
                    .hasMessageContaining("-Djuno.port=<port>");
        }

        @Test
        @DisplayName("Picks the serial port of a board also listed on a DFU port")
        void shouldPickTheSerialPortOfABoardAlsoListedOnADfuPort() {
            // Given
            String nanoEsp32 = Board.NANO_ESP32.fqbn();
            FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                    detected("1-1.3", "dfu", nanoEsp32),
                    detected("/dev/cu.usbmodem123", "serial", nanoEsp32)));
            ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

            // When
            String port = cli.resolvePort(nanoEsp32, Optional.empty());

            // Then
            assertThat(port).isEqualTo("/dev/cu.usbmodem123");
        }

        @Test
        @DisplayName("Asks for a port when several matching serial ports are connected")
        void shouldAskForAPortWhenSeveralMatchingSerialPortsAreConnected() {
            // Given
            FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                    detected("COM3", "serial", FQBN), detected("COM4", "serial", FQBN)));
            ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

            // When & Then
            assertThatThrownBy(() -> cli.resolvePort(FQBN, Optional.empty()))
                    .isInstanceOf(ArduinoCliException.class)
                    .hasMessageContaining("Multiple boards");
        }

        @Test
        @DisplayName("Reports when no matching board is connected")
        void shouldReportWhenNoMatchingBoardIsConnected() {
            // Given
            FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                    detected("COM4", Board.UNO_R3.fqbn())));
            ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

            // When & Then
            assertThatThrownBy(() -> cli.resolvePort(FQBN, Optional.empty()))
                    .isInstanceOf(ArduinoCliException.class)
                    .hasMessageContaining("No connected board matching " + FQBN);
        }
    }

    @Nested
    @DisplayName("Uploading and monitoring")
    class UploadingAndMonitoring {
        @Test
        @DisplayName("Uploads and monitors with the expected arguments")
        void shouldUploadAndMonitorWithTheExpectedArguments() {
            // Given
            FakeExecutor executor = new FakeExecutor().respond(0, "uploaded").respond(0, "");
            ArduinoCli cli = new ArduinoCli("/opt/arduino-cli", executor, ignored -> { });
            Path sketch = Path.of("target/juno/Blink");

            // When
            cli.upload(FQBN, "COM3", sketch);
            cli.monitor("COM3", 115200);

            // Then
            assertThat(executor.invocations()).containsExactly(
                    captured(List.of("/opt/arduino-cli", "upload", "--port", "COM3",
                            "--fqbn", FQBN, sketch.toAbsolutePath().normalize().toString())),
                    // Deliberately no --fqbn here: arduino-cli monitor 1.5.1 exits immediately with no
                    // diagnostic output when --fqbn is passed alongside --port (see ArduinoCli.monitor).
                    interactive(List.of("/opt/arduino-cli", "monitor", "--port", "COM3",
                            "--config", "baudrate=115200")));
        }
    }

    private static String boardList(String... ports) {
        return "{\"detected_ports\":[" + String.join(",", ports) + "]}";
    }

    private static String detected(String address, String fqbn) {
        String matchingBoards = fqbn.isEmpty()
                ? ""
                : "\"matching_boards\":[{\"name\":\"Board\",\"fqbn\":\"" + fqbn + "\"}],";
        return "{" + matchingBoards + "\"port\":{\"address\":\"" + address + "\"}}";
    }

    private static String detected(String address, String protocol, String fqbn) {
        return "{\"matching_boards\":[{\"name\":\"Board\",\"fqbn\":\"" + fqbn + "\"}],"
                + "\"port\":{\"address\":\"" + address + "\",\"protocol\":\"" + protocol + "\"}}";
    }

    private static String detectedUsbSerial(String address, String vendorId) {
        return "{\"port\":{\"address\":\"" + address + "\",\"protocol\":\"serial\","
                + "\"properties\":{\"vid\":\"" + vendorId + "\"}}}";
    }

    private static Command captured(List<String> arguments) {
        return new Command(arguments, Command.Mode.CAPTURED);
    }

    private static Command interactive(List<String> arguments) {
        return new Command(arguments, Command.Mode.INTERACTIVE);
    }
}
