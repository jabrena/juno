package io.github.jabrena.juno.arduino;

import io.github.jabrena.juno.board.Board;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * One board's recorded static RAM for the core-drift check: the Arduino core it was measured with, the figure, and the
 * growth it tolerates.
 *
 * @param board the board the figure belongs to
 * @param core the core and version, written {@code platform@version}, for example {@code arduino:avr@1.8.8}
 * @param baselineBytes the static RAM the baseline program used with that core
 * @param marginBytes how much the figure may grow before the check fails
 */
record CoreBaseline(Board board, String core, int baselineBytes, int marginBytes) {

    static final String RESOURCE = "/arduino/core-drift-baselines.txt";

    /** The baselines recorded in {@code core-drift-baselines.txt}, one per data line, in file order. */
    static List<CoreBaseline> load() throws IOException {
        try (InputStream in = CoreBaseline.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing " + RESOURCE + " on the test classpath");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static List<CoreBaseline> parse(String text) {
        List<CoreBaseline> baselines = new ArrayList<>();
        for (String line : text.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] columns = trimmed.split("\\s+");
            if (columns.length != 4) {
                throw new IllegalArgumentException("Expected board, core, baseline and margin in: " + line);
            }
            baselines.add(new CoreBaseline(Board.fromId(columns[0]), columns[1], Integer.parseInt(columns[2]),
                    Integer.parseInt(columns[3])));
        }
        return List.copyOf(baselines);
    }

    /** The platform part of {@link #core}, for example {@code arduino:avr}. */
    String platform() {
        return core.substring(0, core.indexOf('@'));
    }

    /** The version part of {@link #core}, for example {@code 1.8.8}. */
    String version() {
        return core.substring(core.indexOf('@') + 1);
    }
}
