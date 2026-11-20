package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.classfile.JavaClass;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Every board the entry point's {@code @Board} annotation names, in declaration order. Without the annotation the
 * program is unrestricted and its only board is the requested one.
 *
 * @param boards the declared boards, never empty
 */
record DeclaredBoards(List<Board> boards) {

    DeclaredBoards {
        boards = List.copyOf(boards);
    }

    /** Reads {@code mainClass}'s {@code @Board}, falling back to {@code requestedBoardId} when it has none. */
    static DeclaredBoards of(JavaClass mainClass, Optional<String> requestedBoardId) {
        List<String> boardApiClassNames = mainClass.boardApiClassNames();
        if (boardApiClassNames.isEmpty()) {
            if (requestedBoardId.isEmpty()) {
                throw new CompileException("Target board is required when the entry point has no @Board annotation; "
                        + "pass -Djuno.board=<id> for the Maven plugin or --board=<id> for the standalone CLI");
            }
            return new DeclaredBoards(List.of(Board.fromId(requestedBoardId.get())));
        }
        return new DeclaredBoards(boardApiClassNames.stream().map(Board::fromApiClassName).distinct().toList());
    }

    /**
     * The single board this build targets: {@code requestedBoardId}, which must be declared, or the only declared
     * board. Declaring more than one and requesting none fails rather than depend on declaration order.
     */
    Board target(Optional<String> requestedBoardId) {
        if (requestedBoardId.isPresent()) {
            Board requested = Board.fromId(requestedBoardId.get());
            if (!boards.contains(requested)) {
                throw new CompileException("Requested board '" + requestedBoardId.get()
                        + "' is not one of @Board's declared boards: " + displayNames());
            }
            return requested;
        }
        if (boards.size() > 1) {
            throw new CompileException("@Board declares multiple boards (" + displayNames()
                    + "); pass the target board explicitly: -Djuno.board=<id> for the Maven plugin, "
                    + "--board=<id> for the standalone CLI");
        }
        return boards.get(0);
    }

    /** The declared boards other than {@code target}, in declaration order. */
    List<Board> others(Board target) {
        return boards.stream().filter(board -> board != target).toList();
    }

    private String displayNames() {
        return boards.stream().map(Board::displayName).collect(Collectors.joining(", "));
    }
}
