package io.github.jabrena.juno.maven;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

final class ArduinoCli {
    private final String executable;
    private final CommandExecutor executor;
    private final Consumer<String> output;

    ArduinoCli(String executable, Consumer<String> output) {
        this(executable, new SystemCommandExecutor(), output);
    }

    ArduinoCli(String executable, CommandExecutor executor, Consumer<String> output) {
        this.executable = requireText(executable, "Arduino CLI executable");
        this.executor = executor;
        this.output = output;
    }

    void installCore(String platform) {
        runChecked(List.of(executable, "core", "install", requireText(platform, "platform")), false);
    }

    void installLibrary(String library) {
        runChecked(List.of(executable, "lib", "install", requireText(library, "library")), false);
    }

    void compile(String fqbn, Path sketchDirectory) {
        runChecked(List.of(executable, "compile", "--fqbn", requireText(fqbn, "FQBN"),
                sketchDirectory.toAbsolutePath().normalize().toString()), false);
    }

    void upload(String fqbn, String port, Path sketchDirectory) {
        runChecked(List.of(executable, "upload", "--port", requireText(port, "port"),
                "--fqbn", requireText(fqbn, "FQBN"),
                sketchDirectory.toAbsolutePath().normalize().toString()), false);
    }

    /**
     * Deliberately omits {@code --fqbn}: {@code arduino-cli monitor} (confirmed on 1.5.1) exits
     * immediately with status 0 and no diagnostic output whenever {@code --fqbn} is passed alongside
     * {@code --port}, regardless of environment — silently doing nothing instead of opening the
     * monitor. {@code --port} plus an explicit {@code --config baudrate=...} (always supplied by
     * {@code MonitorMojo}) is sufficient; {@code --fqbn} isn't needed for baud-rate defaulting here.
     */
    void monitor(String port, int baudRate) {
        if (baudRate <= 0) {
            throw new ArduinoCliException("Monitor baud rate must be greater than zero");
        }
        runChecked(List.of(executable, "monitor", "--port", requireText(port, "port"),
                "--config", "baudrate=" + baudRate), true);
    }

    String resolvePort(String fqbn, String configuredPort) {
        String requiredFqbn = requireText(fqbn, "FQBN");
        List<BoardListJsonParser.DetectedPort> ports = boardList();
        List<String> matching = ports.stream()
                .filter(port -> port.fqbns().contains(requiredFqbn))
                .map(BoardListJsonParser.DetectedPort::address)
                .toList();

        if (configuredPort != null && !configuredPort.isBlank()) {
            String requested = configuredPort.trim();
            if (!matching.contains(requested)) {
                throw new ArduinoCliException("Configured port '" + requested + "' is not a connected "
                        + requiredFqbn + " board. Matching ports: " + display(matching));
            }
            return requested;
        }
        if (matching.isEmpty()) {
            throw new ArduinoCliException("No connected board matching " + requiredFqbn
                    + " was found. Connect the board or set -Djuno.port=<port> after checking juno:boards.");
        }
        if (matching.size() > 1) {
            throw new ArduinoCliException("Multiple boards matching " + requiredFqbn + " were found: "
                    + String.join(", ", matching) + ". Select one with -Djuno.port=<port>.");
        }
        return matching.getFirst();
    }

    List<BoardListJsonParser.DetectedPort> boardList() {
        CommandResult result = executor.execute(List.of(executable, "board", "list", "--json"), false);
        checkExit(List.of(executable, "board", "list", "--json"), result);
        return BoardListJsonParser.parse(result.output());
    }

    private void runChecked(List<String> command, boolean interactive) {
        CommandResult result = executor.execute(command, interactive);
        if (!interactive && !result.output().isBlank()) {
            output.accept(result.output().stripTrailing());
        }
        checkExit(command, result);
    }

    private static void checkExit(List<String> command, CommandResult result) {
        if (result.exitCode() != 0) {
            String detail = result.output().isBlank() ? "" : ": " + result.output().strip();
            throw new ArduinoCliException("Command failed with exit code " + result.exitCode() + ": "
                    + String.join(" ", command) + detail);
        }
    }

    private static String display(List<String> values) {
        return values.isEmpty() ? "(none)" : String.join(", ", values);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ArduinoCliException(label + " must not be blank");
        }
        return value.trim();
    }
}
