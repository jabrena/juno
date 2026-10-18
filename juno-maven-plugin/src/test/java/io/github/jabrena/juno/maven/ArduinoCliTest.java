package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArduinoCliTest {
    private static final String FQBN = Board.UNO_R4_WIFI.fqbn();

    @Test
    void compilesSketchWithExpectedArguments() {
        FakeExecutor executor = new FakeExecutor().respond(0, "compiled");
        List<String> output = new ArrayList<>();
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, output::add);
        Path sketch = Path.of("target/juno/Blink");

        cli.compile(FQBN, sketch);

        assertThat(executor.invocations()).containsExactly(captured(List.of(
                "arduino-cli", "compile", "--fqbn", FQBN,
                sketch.toAbsolutePath().normalize().toString())));
        assertThat(output).containsExactly("compiled");
    }

    @Test
    void compilesAClassicUnoR3WithoutExtraBuildProperties() {
        FakeExecutor executor = new FakeExecutor().respond(0, "compiled");
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });
        Path sketch = Path.of("target/juno/Blink");

        cli.compile(Board.UNO_R3.fqbn(), sketch);

        assertThat(executor.invocations()).containsExactly(captured(List.of(
                "arduino-cli", "compile", "--fqbn", Board.UNO_R3.fqbn(),
                sketch.toAbsolutePath().normalize().toString())));
    }

    @Test
    void enablesSdFatSoftwareSpiForUnoWiFiRev2() {
        FakeExecutor executor = new FakeExecutor().respond(0, "compiled");
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });
        Path sketch = Path.of("target/juno/WifiStatusTFT");

        cli.compile(Board.UNO_R3_WIFI_REV2.fqbn(), sketch);

        assertThat(executor.invocations()).containsExactly(captured(List.of(
                "arduino-cli", "compile", "--fqbn", Board.UNO_R3_WIFI_REV2.fqbn(),
                "--build-property", "compiler.cpp.extra_flags=-DSPI_DRIVER_SELECT=3",
                sketch.toAbsolutePath().normalize().toString())));
    }

    @Test
    void disablesSdFatSerialHelpersForUnoQ() {
        FakeExecutor executor = new FakeExecutor().respond(0, "compiled");
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });
        Path sketch = Path.of("target/juno/DashboardTFT");

        cli.compile(Board.UNO_Q.fqbn(), sketch);

        assertThat(executor.invocations()).containsExactly(captured(List.of(
                "arduino-cli", "compile", "--fqbn", Board.UNO_Q.fqbn(),
                "--build-property", "compiler.cpp.extra_flags=-DENABLE_ARDUINO_SERIAL=0",
                sketch.toAbsolutePath().normalize().toString())));
    }

    @Test
    void installsCoreAndLibraryWithExpectedArguments() {
        FakeExecutor executor = new FakeExecutor().respond(0, "core installed").respond(0, "lib installed");
        List<String> output = new ArrayList<>();
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, output::add);

        cli.installCore("arduino:renesas_uno");
        cli.installLibrary("ESP_SSLClient");

        assertThat(executor.invocations()).containsExactly(
                captured(List.of("arduino-cli", "core", "install", "arduino:renesas_uno")),
                captured(List.of("arduino-cli", "lib", "install", "ESP_SSLClient")));
        assertThat(output).containsExactly("core installed", "lib installed");
    }

    @Test
    void discoversOnlyMatchingBoard() {
        FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                detected("/dev/cu.Bluetooth", ""),
                detected("/dev/cu.usbmodem123", FQBN)));
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

        assertThat(cli.resolvePort(FQBN, Optional.empty())).isEqualTo("/dev/cu.usbmodem123");
        assertThat(executor.invocations()).containsExactly(
                captured(List.of("arduino-cli", "board", "list", "--json")));
    }

    @Test
    void validatesConfiguredPortAgainstBoardAndFqbn() {
        FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                detected("COM3", FQBN), detected("COM4", Board.UNO_R3.fqbn())));
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

        assertThat(cli.resolvePort(FQBN, Optional.of(" COM3 "))).isEqualTo("COM3");
    }

    @Test
    void rejectsConfiguredPortForDifferentBoard() {
        FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                detected("COM3", FQBN), detected("COM4", Board.UNO_R3.fqbn())));
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

        assertThatThrownBy(() -> cli.resolvePort(FQBN, Optional.of("COM4")))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("Configured port 'COM4'")
                .hasMessageContaining("Matching ports: COM3");
    }

    @Test
    void asksForPortWhenMultipleMatchingBoardsAreConnected() {
        FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                detected("COM3", FQBN), detected("COM4", FQBN)));
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

        assertThatThrownBy(() -> cli.resolvePort(FQBN, Optional.empty()))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("Multiple boards")
                .hasMessageContaining("-Djuno.port=<port>");
    }

    @Test
    void reportsWhenNoMatchingBoardIsConnected() {
        FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                detected("COM4", Board.UNO_R3.fqbn())));
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

        assertThatThrownBy(() -> cli.resolvePort(FQBN, Optional.empty()))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("No connected board matching " + FQBN);
    }

    @Test
    void uploadsAndMonitorsWithExpectedArguments() {
        FakeExecutor executor = new FakeExecutor().respond(0, "uploaded").respond(0, "");
        ArduinoCli cli = new ArduinoCli("/opt/arduino-cli", executor, ignored -> { });
        Path sketch = Path.of("target/juno/Blink");

        cli.upload(FQBN, "COM3", sketch);
        cli.monitor("COM3", 115200);

        assertThat(executor.invocations()).containsExactly(
                captured(List.of("/opt/arduino-cli", "upload", "--port", "COM3",
                        "--fqbn", FQBN, sketch.toAbsolutePath().normalize().toString())),
                // Deliberately no --fqbn here: arduino-cli monitor 1.5.1 exits immediately with no
                // diagnostic output when --fqbn is passed alongside --port (see ArduinoCli.monitor).
                interactive(List.of("/opt/arduino-cli", "monitor", "--port", "COM3",
                        "--config", "baudrate=115200")));
    }

    @Test
    void includesArduinoCliOutputWhenCommandFails() {
        FakeExecutor executor = new FakeExecutor().respond(2, "platform not installed");
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

        assertThatThrownBy(() -> cli.compile(FQBN, Path.of("target/juno/Blink")))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("exit code 2")
                .hasMessageContaining("platform not installed");
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

    private static Command captured(List<String> arguments) {
        return new Command(arguments, Command.Mode.CAPTURED);
    }

    private static Command interactive(List<String> arguments) {
        return new Command(arguments, Command.Mode.INTERACTIVE);
    }
}
