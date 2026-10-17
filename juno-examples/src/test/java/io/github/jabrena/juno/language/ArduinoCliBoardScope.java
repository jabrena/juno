package io.github.jabrena.juno.language;

import io.github.jabrena.juno.board.Board;

import java.util.List;
import java.util.Locale;

/** Selects boards for language examples that do not declare {@code @Board}. */
enum ArduinoCliBoardScope {
    ALL(List.of(Board.values())),
    REPRESENTATIVE(List.of(Board.UNO_R4_WIFI));

    static final String PROPERTY = "juno.arduinoCliBoardScope";
    private static final String VALUES = "all, representative";

    private final List<Board> genericBoards;

    ArduinoCliBoardScope(List<Board> genericBoards) {
        this.genericBoards = genericBoards;
    }

    static ArduinoCliBoardScope configured() {
        return parse(System.getProperty(PROPERTY, "all"));
    }

    static ArduinoCliBoardScope parse(String configured) {
        try {
            return valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported " + PROPERTY + " '" + configured + "'; expected one of: " + VALUES, exception);
        }
    }

    List<Board> genericBoards() {
        return genericBoards;
    }
}
