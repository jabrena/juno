package io.github.jabrena.juno.maven;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ArduinoCli {
    private static final Pattern JSON_STRING_FIELD = Pattern.compile(
            "\\\"(?<name>[^\\\"]+)\\\"\\s*:\\s*\\\"(?<value>(?:\\\\.|[^\\\"\\\\])*)\\\"");

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
        List<DetectedPort> ports = boardList();
        List<String> matching = ports.stream()
                .filter(port -> port.fqbns().contains(requiredFqbn))
                .map(DetectedPort::address)
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
                    + " was found. Connect the board or set -Djuno.port=<port> after checking arduino-cli board list.");
        }
        if (matching.size() > 1) {
            throw new ArduinoCliException("Multiple boards matching " + requiredFqbn + " were found: "
                    + String.join(", ", matching) + ". Select one with -Djuno.port=<port>.");
        }
        return matching.getFirst();
    }

    private List<DetectedPort> boardList() {
        CommandResult result = executor.execute(List.of(executable, "board", "list", "--json"), false);
        checkExit(List.of(executable, "board", "list", "--json"), result);
        return parseBoardList(result.output());
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

    static List<DetectedPort> parseBoardList(String json) {
        int field = json.indexOf("\"detected_ports\"");
        int arrayStart = field < 0 ? -1 : json.indexOf('[', field);
        if (arrayStart < 0) {
            throw new ArduinoCliException("Unexpected output from 'arduino-cli board list --json': "
                    + "missing detected_ports array");
        }

        List<DetectedPort> ports = new ArrayList<>();
        for (String object : topLevelObjects(json, arrayStart)) {
            String address = firstField(object, "address");
            if (address == null) {
                continue;
            }
            Set<String> fqbns = new LinkedHashSet<>(fields(object, "fqbn"));
            ports.add(new DetectedPort(address, List.copyOf(fqbns)));
        }
        return List.copyOf(ports);
    }

    private static List<String> topLevelObjects(String json, int arrayStart) {
        List<String> objects = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        int arrayDepth = 0;
        int objectDepth = 0;
        int objectStart = -1;

        for (int index = arrayStart; index < json.length(); index++) {
            char character = json.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
            } else if (character == '[') {
                arrayDepth++;
            } else if (character == ']') {
                arrayDepth--;
                if (arrayDepth == 0) {
                    return objects;
                }
            } else if (character == '{') {
                if (arrayDepth == 1 && objectDepth == 0) {
                    objectStart = index;
                }
                objectDepth++;
            } else if (character == '}') {
                objectDepth--;
                if (arrayDepth == 1 && objectDepth == 0 && objectStart >= 0) {
                    objects.add(json.substring(objectStart, index + 1));
                    objectStart = -1;
                }
            }
        }
        throw new ArduinoCliException("Unexpected output from 'arduino-cli board list --json': "
                + "unterminated detected_ports array");
    }

    private static String firstField(String json, String name) {
        List<String> values = fields(json, name);
        return values.isEmpty() ? null : values.getFirst();
    }

    private static List<String> fields(String json, String name) {
        List<String> values = new ArrayList<>();
        Matcher matcher = JSON_STRING_FIELD.matcher(json);
        while (matcher.find()) {
            if (matcher.group("name").equals(name)) {
                values.add(unescapeJson(matcher.group("value")));
            }
        }
        return values;
    }

    private static String unescapeJson(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '\\') {
                result.append(character);
                continue;
            }
            if (++index >= value.length()) {
                throw new ArduinoCliException("Invalid JSON escape in Arduino CLI output");
            }
            char escaped = value.charAt(index);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (index + 4 >= value.length()) {
                        throw new ArduinoCliException("Invalid Unicode escape in Arduino CLI output");
                    }
                    try {
                        result.append((char) Integer.parseInt(value.substring(index + 1, index + 5), 16));
                    } catch (NumberFormatException exception) {
                        throw new ArduinoCliException("Invalid Unicode escape in Arduino CLI output", exception);
                    }
                    index += 4;
                }
                default -> throw new ArduinoCliException("Invalid JSON escape in Arduino CLI output: \\" + escaped);
            }
        }
        return result.toString();
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

    record DetectedPort(String address, List<String> fqbns) {
    }
}
