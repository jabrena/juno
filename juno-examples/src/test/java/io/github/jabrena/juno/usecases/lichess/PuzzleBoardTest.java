package io.github.jabrena.juno.usecases.lichess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PuzzleBoardTest {
    @Test
    void loadsFenAndAppliesCastling() {
        PuzzleBoard board = new PuzzleBoard();
        byte[] fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1".getBytes();

        assertTrue(board.loadFen(fen, fen.length));
        int castle = PuzzleBoard.parseUci("e1g1".getBytes(), 4);
        board.apply(castle);

        assertEquals(PuzzleBoard.KING, board.pieceAt(62));
        assertEquals(PuzzleBoard.ROOK, board.pieceAt(61));
        assertEquals(PuzzleBoard.EMPTY, board.pieceAt(60));
    }

    @Test
    void rejectsMalformedUci() {
        assertEquals(-1, PuzzleBoard.parseUci("e9e4".getBytes(), 4));
        assertEquals(-1, PuzzleBoard.parseUci("e2e4x".getBytes(), 5));
    }
}
