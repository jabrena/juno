package io.github.jabrena.juno.language;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.board.Board;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Selects the boards included in one Arduino CLI pipeline shard. */
final class ArduinoCliBoards {
    static final String PROPERTY = "juno.arduinoCliBoards";
    private static final String VALUES = Arrays.stream(Board.values()).map(Board::id).collect(Collectors.joining(", "));

    private final Set<Board> boards;

    private ArduinoCliBoards(Set<Board> boards) {
        this.boards = Set.copyOf(boards);
    }

    static ArduinoCliBoards configured() {
        return parse(System.getProperty(PROPERTY, ""));
    }

    static ArduinoCliBoards parse(String configured) {
        if (configured.isBlank()) {
            return new ArduinoCliBoards(EnumSet.allOf(Board.class));
        }
        EnumSet<Board> boards = EnumSet.noneOf(Board.class);
        for (String boardId : configured.split(",", -1)) {
            if (boardId.isBlank()) {
                throw unsupported(configured, null);
            }
            try {
                boards.add(Board.fromId(boardId));
            } catch (CompileException exception) {
                throw unsupported(configured, exception);
            }
        }
        return new ArduinoCliBoards(boards);
    }

    boolean includes(Board board) {
        return boards.contains(board);
    }

    private static IllegalArgumentException unsupported(String configured, Exception cause) {
        return new IllegalArgumentException(
                "Unsupported " + PROPERTY + " '" + configured + "'; expected comma-separated values from: " + VALUES,
                cause);
    }
}
