package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.thirdparty.bearssl.BearSslLibrary;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** The {@code arduino-cli} commands the Maven goals run, behind a {@link CommandExecutor}. */
final class ArduinoCli {
    private static final String UNO_R3_WIFI_REV2_FQBN = "arduino:megaavr:uno2018";
    private static final String UNO_Q_FQBN = "arduino:zephyr:unoq";
    private static final String SDFAT_SOFTWARE_SPI = "compiler.cpp.extra_flags=-DSPI_DRIVER_SELECT=3";
    private static final String SDFAT_WITHOUT_ARDUINO_SERIAL =
            "compiler.cpp.extra_flags=-DENABLE_ARDUINO_SERIAL=0";

    private final String executable;
    private final CommandExecutor executor;
    private final Consumer<String> output;
    private final BearSslLibrary bearSsl;
    private final BoardListJsonParser boardListParser = new BoardListJsonParser();

    ArduinoCli(String executable, Consumer<String> output) {
        this(executable, new SystemCommandExecutor(), output);
    }

    ArduinoCli(String executable, CommandExecutor executor, Consumer<String> output) {
        this(executable, executor, output, new BearSslLibrary());
    }

    ArduinoCli(String executable, CommandExecutor executor, Consumer<String> output, BearSslLibrary bearSsl) {
        this.executable = requireText(executable, "Arduino CLI executable");
        this.executor = executor;
        this.output = output;
        this.bearSsl = bearSsl;
    }

    void installCore(String platform) {
        run(captured("core", "install", requireText(platform, "platform")));
    }

    void installCore(String platform, String additionalUrl) {
        run(captured("core", "install", requireText(platform, "platform"), "--additional-urls",
                requireText(additionalUrl, "additional URL")));
    }

    void installLibrary(String library) {
        run(captured("lib", "install", requireText(library, "library")));
    }

    void compile(String fqbn, Path sketchDirectory) {
        String requiredFqbn = requireText(fqbn, "FQBN");
        List<String> arguments = new ArrayList<>(List.of("compile", "--fqbn", requiredFqbn));
        if (UNO_R3_WIFI_REV2_FQBN.equals(requiredFqbn)) {
            arguments.addAll(List.of("--build-property", SDFAT_SOFTWARE_SPI));
        } else if (UNO_Q_FQBN.equals(requiredFqbn)) {
            // The Zephyr core has no TLS server, so HttpServer.beginTls compiles against the bundled BearSSL.
            arguments.addAll(List.of("--build-property", SDFAT_WITHOUT_ARDUINO_SERIAL,
                    "--library", absolute(bundledBearSsl(sketchDirectory))));
        }
        arguments.add(absolute(sketchDirectory));
        run(captured(arguments.toArray(String[]::new)));
    }

    /** Installs BearSSL beside the generated sketches ({@code target/juno/libraries}), once per version. */
    private Path bundledBearSsl(Path sketchDirectory) {
        Path sketches = sketchDirectory.toAbsolutePath().normalize().getParent();
        Path libraries = (sketches != null ? sketches : sketchDirectory.toAbsolutePath()).resolve("libraries");
        try {
            return bearSsl.installInto(libraries);
        } catch (IOException exception) {
            throw new ArduinoCliException("Could not install " + BearSslLibrary.NAME + " into " + libraries,
                    exception);
        }
    }

    void upload(String fqbn, String port, Path sketchDirectory) {
        run(captured("upload", "--port", requireText(port, "port"), "--fqbn", requireText(fqbn, "FQBN"),
                absolute(sketchDirectory)));
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
        run(command(Command.Mode.INTERACTIVE, "monitor", "--port", requireText(port, "port"),
                "--config", "baudrate=" + baudRate));
    }

    /**
     * The port of the one connected board with {@code fqbn}, or {@code configuredPort} after checking such a board
     * is really connected there. A board listed on several protocols, such as the Nano ESP32's DFU and serial ports,
     * resolves to its only serial port: the monitor needs it, and the upload tool finds the board by itself.
     */
    String resolvePort(String fqbn, Optional<String> configuredPort) {
        String requiredFqbn = requireText(fqbn, "FQBN");
        List<DetectedPort> matchingPorts = boardList().stream()
                .filter(port -> port.hosts(requiredFqbn))
                .toList();
        List<String> matching = addresses(matchingPorts);
        return configuredPort.map(String::trim).filter(port -> !port.isBlank())
                .map(requested -> requireConnected(requested, requiredFqbn, matching))
                .orElseGet(() -> onlyMatching(requiredFqbn, matching, serialAddresses(matchingPorts)));
    }

    private List<String> addresses(List<DetectedPort> ports) {
        return ports.stream().map(DetectedPort::address).toList();
    }

    private List<String> serialAddresses(List<DetectedPort> ports) {
        return addresses(ports.stream().filter(port -> port.protocol().equals("serial")).toList());
    }

    List<DetectedPort> boardList() {
        Command command = captured("board", "list", "--json");
        CommandResult result = executor.execute(command);
        checkExit(command, result);
        return boardListParser.parse(result.output());
    }

    private String requireConnected(String requested, String fqbn, List<String> matching) {
        if (!matching.contains(requested)) {
            throw new ArduinoCliException("Configured port '" + requested + "' is not a connected " + fqbn
                    + " board. Matching ports: " + (matching.isEmpty() ? "(none)" : String.join(", ", matching)));
        }
        return requested;
    }

    private String onlyMatching(String fqbn, List<String> matching, List<String> serialMatching) {
        return switch (matching.size()) {
            case 0 -> throw new ArduinoCliException("No connected board matching " + fqbn
                    + " was found. Connect the board or set -Djuno.port=<port> after checking juno:boards.");
            case 1 -> matching.getFirst();
            default -> {
                if (serialMatching.size() == 1) {
                    yield serialMatching.getFirst();
                }
                throw new ArduinoCliException("Multiple boards matching " + fqbn + " were found: "
                        + String.join(", ", matching) + ". Select one with -Djuno.port=<port>.");
            }
        };
    }

    private Command captured(String... arguments) {
        return command(Command.Mode.CAPTURED, arguments);
    }

    private Command command(Command.Mode mode, String... arguments) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(executable);
        commandLine.addAll(List.of(arguments));
        return new Command(commandLine, mode);
    }

    private void run(Command command) {
        CommandResult result = executor.execute(command);
        if (!command.isInteractive() && result.hasOutput()) {
            output.accept(result.output().stripTrailing());
        }
        checkExit(command, result);
    }

    private void checkExit(Command command, CommandResult result) {
        if (!result.succeeded()) {
            String detail = result.hasOutput() ? ": " + result.output().strip() : "";
            throw new ArduinoCliException("Command failed with exit code " + result.exitCode() + ": "
                    + command.display() + detail);
        }
    }

    private String absolute(Path directory) {
        return directory.toAbsolutePath().normalize().toString();
    }

    private String requireText(@Nullable String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ArduinoCliException(label + " must not be blank");
        }
        return value.trim();
    }
}
