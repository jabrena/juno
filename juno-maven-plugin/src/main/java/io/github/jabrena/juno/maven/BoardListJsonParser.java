package io.github.jabrena.juno.maven;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal hand-rolled parser for {@code arduino-cli board list --json} output, avoiding a JSON
 * library dependency for a single, narrow field extraction.
 */
final class BoardListJsonParser {
    private static final Pattern JSON_STRING_FIELD = Pattern.compile(
            "\\\"(?<name>[^\\\"]+)\\\"\\s*:\\s*\\\"(?<value>(?:\\\\.|[^\\\"\\\\])*)\\\"");

    private BoardListJsonParser() {
    }

    static List<DetectedPort> parse(String json) {
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
            String protocol = firstField(object, "protocol");
            ports.add(new DetectedPort(address, protocol == null ? "" : protocol,
                    List.copyOf(new LinkedHashSet<>(fields(object, "name"))), List.copyOf(fqbns)));
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

    private static @Nullable String firstField(String json, String name) {
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

    record DetectedPort(String address, String protocol, List<String> boardNames, List<String> fqbns) {
    }
}
