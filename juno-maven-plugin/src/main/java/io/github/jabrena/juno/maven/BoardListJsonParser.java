package io.github.jabrena.juno.maven;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Minimal hand-rolled parser for {@code arduino-cli board list --json} output, avoiding a JSON
 * library dependency for a single, narrow field extraction.
 */
final class BoardListJsonParser {
    private static final Pattern JSON_STRING_FIELD = Pattern.compile(
            "\"(?<name>[^\"]+)\"\\s*:\\s*\"(?<value>(?:\\\\.|[^\"\\\\])*)\"");

    List<DetectedPort> parse(String json) {
        int field = json.indexOf("\"detected_ports\"");
        int arrayStart = field < 0 ? -1 : json.indexOf('[', field);
        if (arrayStart < 0) {
            throw malformed("missing detected_ports array");
        }
        List<DetectedPort> ports = new ArrayList<>();
        for (String object : new ArrayScanner(json, arrayStart).topLevelObjects()) {
            firstField(object, "address").ifPresent(address -> ports.add(new DetectedPort(address,
                    firstField(object, "protocol").orElse(""),
                    distinct(fields(object, "name")), distinct(fields(object, "fqbn")),
                    firstField(object, "vid").orElse(""))));
        }
        return List.copyOf(ports);
    }

    private Optional<String> firstField(String json, String name) {
        return fields(json, name).stream().findFirst();
    }

    /** Every string value of a field called {@code name}, at any nesting level, in document order. */
    private List<String> fields(String json, String name) {
        return JSON_STRING_FIELD.matcher(json).results()
                .filter(match -> match.group("name").equals(name))
                .map(match -> unescape(match.group("value")))
                .toList();
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private String unescape(String value) {
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
                    result.append(unicode(value, index + 1));
                    index += 4;
                }
                default -> throw new ArduinoCliException("Invalid JSON escape in Arduino CLI output: \\" + escaped);
            }
        }
        return result.toString();
    }

    private char unicode(String value, int start) {
        if (start + 4 > value.length()) {
            throw new ArduinoCliException("Invalid Unicode escape in Arduino CLI output");
        }
        try {
            return (char) Integer.parseInt(value.substring(start, start + 4), 16);
        } catch (NumberFormatException exception) {
            throw new ArduinoCliException("Invalid Unicode escape in Arduino CLI output", exception);
        }
    }

    private ArduinoCliException malformed(String problem) {
        return new ArduinoCliException("Unexpected output from 'arduino-cli board list --json': " + problem);
    }

    /** Walks a JSON array character by character, skipping string contents, to cut out its top-level objects. */
    private final class ArrayScanner {
        private final String json;
        private int index;
        private boolean inString;
        private boolean escaped;
        private int arrayDepth;
        private int objectDepth;
        private int objectStart = -1;

        ArrayScanner(String json, int arrayStart) {
            this.json = json;
            this.index = arrayStart;
        }

        List<String> topLevelObjects() {
            List<String> objects = new ArrayList<>();
            for (; index < json.length(); index++) {
                char character = json.charAt(index);
                if (inString) {
                    skipStringCharacter(character);
                    continue;
                }
                switch (character) {
                    case '"' -> inString = true;
                    case '[' -> arrayDepth++;
                    case ']' -> {
                        if (--arrayDepth == 0) {
                            return objects;
                        }
                    }
                    case '{' -> {
                        if (arrayDepth == 1 && objectDepth == 0) {
                            objectStart = index;
                        }
                        objectDepth++;
                    }
                    case '}' -> {
                        objectDepth--;
                        if (arrayDepth == 1 && objectDepth == 0 && objectStart >= 0) {
                            objects.add(json.substring(objectStart, index + 1));
                            objectStart = -1;
                        }
                    }
                    default -> {
                    }
                }
            }
            throw malformed("unterminated detected_ports array");
        }

        private void skipStringCharacter(char character) {
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                inString = false;
            }
        }
    }
}
