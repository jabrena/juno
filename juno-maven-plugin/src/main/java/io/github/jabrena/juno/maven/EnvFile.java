package io.github.jabrena.juno.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@code .env} file: {@code KEY=VALUE} lines, where blank lines, {@code #} comments and lines without {@code =}
 * are ignored and both sides are trimmed. A later line for the same key wins.
 */
record EnvFile(Path path) {
    boolean exists() {
        return Files.isRegularFile(path);
    }

    Map<String, String> entries() throws IOException {
        return parse(Files.readAllLines(path, StandardCharsets.UTF_8));
    }

    Map<String, String> parse(List<String> lines) {
        Map<String, String> entries = new LinkedHashMap<>();
        lines.stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#") && line.contains("="))
                .forEach(line -> {
                    int separator = line.indexOf('=');
                    entries.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
                });
        return entries;
    }
}
