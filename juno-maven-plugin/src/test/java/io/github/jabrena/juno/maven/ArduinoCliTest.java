package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArduinoCliTest {
    private static final String FQBN = "arduino:renesas_uno:unor4wifi";

    @Test
    void compilesSketchWithExpectedArguments() {
        FakeExecutor executor = new FakeExecutor().respond(0, "compiled");
        List<String> output = new ArrayList<>();
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, output::add);
        Path sketch = Path.of("target/juno/Blink");

        cli.compile(FQBN, sketch);

        assertThat(executor.invocations()).containsExactly(new Invocation(List.of(
                "arduino-cli", "compile", "--fqbn", FQBN,
                sketch.toAbsolutePath().normalize().toString()), false));
        assertThat(output).containsExactly("compiled");
    }

    @Test
    void installsCoreAndLibraryWithExpectedArguments() {
        FakeExecutor executor = new FakeExecutor().respond(0, "core installed").respond(0, "lib installed");
        List<String> output = new ArrayList<>();
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, output::add);

        cli.installCore("arduino:renesas_uno");
        cli.installLibrary("ESP_SSLClient");

        assertThat(executor.invocations()).containsExactly(
                new Invocation(List.of("arduino-cli", "core", "install", "arduino:renesas_uno"), false),
                new Invocation(List.of("arduino-cli", "lib", "install", "ESP_SSLClient"), false));
        assertThat(output).containsExactly("core installed", "lib installed");
    }

    @Test
    void discoversOnlyMatchingBoard() {
        FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                detected("/dev/cu.Bluetooth", ""),
                detected("/dev/cu.usbmodem123", FQBN)));
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

        assertThat(cli.resolvePort(FQBN, null)).isEqualTo("/dev/cu.usbmodem123");
        assertThat(executor.invocations()).containsExactly(new Invocation(
                List.of("arduino-cli", "board", "list", "--json"), false));
    }

    @Test
    void validatesConfiguredPortAgainstBoardAndFqbn() {
        FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                detected("COM3", FQBN), detected("COM4", "arduino:avr:uno")));
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

        assertThat(cli.resolvePort(FQBN, " COM3 ")).isEqualTo("COM3");
    }

    @Test
    void rejectsConfiguredPortForDifferentBoard() {
        FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                detected("COM3", FQBN), detected("COM4", "arduino:avr:uno")));
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

        assertThatThrownBy(() -> cli.resolvePort(FQBN, "COM4"))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("Configured port 'COM4'")
                .hasMessageContaining("Matching ports: COM3");
    }

    @Test
    void asksForPortWhenMultipleMatchingBoardsAreConnected() {
        FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                detected("COM3", FQBN), detected("COM4", FQBN)));
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

        assertThatThrownBy(() -> cli.resolvePort(FQBN, null))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("Multiple boards")
                .hasMessageContaining("-Djuno.port=<port>");
    }

    @Test
    void reportsWhenNoMatchingBoardIsConnected() {
        FakeExecutor executor = new FakeExecutor().respond(0, boardList(
                detected("COM4", "arduino:avr:uno")));
        ArduinoCli cli = new ArduinoCli("arduino-cli", executor, ignored -> { });

        assertThatThrownBy(() -> cli.resolvePort(FQBN, null))
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
                new Invocation(List.of("/opt/arduino-cli", "upload", "--port", "COM3",
                        "--fqbn", FQBN, sketch.toAbsolutePath().normalize().toString()), false),
                // Deliberately no --fqbn here: arduino-cli monitor 1.5.1 exits immediately with no
                // diagnostic output when --fqbn is passed alongside --port (see ArduinoCli.monitor).
                new Invocation(List.of("/opt/arduino-cli", "monitor", "--port", "COM3",
                        "--config", "baudrate=115200"), true));
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

    @Test
    void rejectsMalformedBoardListJson() {
        assertThatThrownBy(() -> ArduinoCli.parseBoardList("{}"))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("missing detected_ports array");
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

    private record Invocation(List<String> command, boolean interactive) {
    }

    private static final class FakeExecutor implements CommandExecutor {
        private final Deque<CommandResult> responses = new ArrayDeque<>();
        private final List<Invocation> invocations = new ArrayList<>();

        FakeExecutor respond(int exitCode, String output) {
            responses.addLast(new CommandResult(exitCode, output));
            return this;
        }

        @Override
        public CommandResult execute(List<String> command, boolean interactive) {
            invocations.add(new Invocation(List.copyOf(command), interactive));
            return responses.removeFirst();
        }

        List<Invocation> invocations() {
            return List.copyOf(invocations);
        }
    }
}
