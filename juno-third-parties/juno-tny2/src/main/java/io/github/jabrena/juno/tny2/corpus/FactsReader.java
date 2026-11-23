package io.github.jabrena.juno.tny2.corpus;

import io.github.jabrena.juno.tny2.Tny2FormatException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads facts in the TINY 2.1 text format, one per line: {@code question | question => answer | answer}. Blank
 * lines and lines starting with {@code #} are skipped. A directory is read as all of its {@code .txt} files in name
 * order.
 */
public final class FactsReader {
    private static final String ARROW = "=>";
    private static final String ALTERNATIVE = "\\|";
    private static final String COMMENT = "#";
    private static final String EXTENSION = ".txt";

    public List<Fact> read(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return parse(path.toString(), Files.readString(path, StandardCharsets.US_ASCII));
        }
        List<Fact> facts = new ArrayList<>();
        try (Stream<Path> files = Files.list(path)) {
            for (Path file : files.filter(file -> file.toString().endsWith(EXTENSION)).sorted().toList()) {
                facts.addAll(read(file));
            }
        }
        return List.copyOf(facts);
    }

    public List<Fact> parse(String source, String text) {
        List<Fact> facts = new ArrayList<>();
        List<String> lines = text.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith(COMMENT)) {
                continue;
            }
            try {
                facts.add(fact(line));
            } catch (IllegalArgumentException | Tny2FormatException invalid) {
                throw new Tny2FormatException("%s:%d: %s".formatted(source, i + 1, invalid.getMessage()));
            }
        }
        return List.copyOf(facts);
    }

    private static Fact fact(String line) {
        String[] sides = line.split(ARROW, -1);
        if (sides.length != 2) {
            throw new IllegalArgumentException("expected 'question | question => answer | answer'");
        }
        return new Fact(alternatives(sides[0]), alternatives(sides[1]));
    }

    private static List<String> alternatives(String side) {
        return Arrays.stream(side.split(ALTERNATIVE)).map(String::strip).filter(text -> !text.isEmpty()).toList();
    }
}
