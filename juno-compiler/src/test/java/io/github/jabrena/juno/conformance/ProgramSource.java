package io.github.jabrena.juno.conformance;

import io.github.jabrena.juno.board.Board;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** One runnable program source in the {@code demo} package and the boards its {@code @Board} selects. */
public record ProgramSource(Path path) {
    private static final String PACKAGE = "demo.";
    private static final String EXTENSION = ".java";

    public ProgramSource {
        Objects.requireNonNull(path);
    }

    public String simpleName() {
        String file = path.getFileName().toString();
        return file.substring(0, file.length() - EXTENSION.length());
    }

    public String mainClass() {
        return PACKAGE + simpleName();
    }

    /**
     * Whether the program runs on {@code board}: every board unless its {@code @Board} names some, so a program can
     * opt out of a board the harness cannot model.
     */
    public boolean targets(Board board) {
        String text = text();
        return !text.contains("@Board(") || text.contains(board.annotationArgument());
    }

    /** Whether the program's {@code @Board} names {@code board}; a program without one declares no board. */
    public boolean declares(Board board) {
        return text().contains(board.annotationArgument());
    }

    private String text() {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
