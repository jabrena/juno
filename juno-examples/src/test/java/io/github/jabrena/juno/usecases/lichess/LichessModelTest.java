package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.mvc.Navigator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LichessModelTest {
    @Test
    void readsThePublicLichessPuzzleShape() {
        LichessModel model = new LichessModel();
        byte[] response = ("{\"puzzle\":{\"rating\":1913,\"plays\":68967,"
                + "\"fen\":\"4k3/8/8/8/8/8/4P3/4K3 w - - 0 1\","
                + "\"solution\":[\"e2e4\",\"e8e7\"]},\"game\":{}}" ).getBytes();

        assertEquals(true, model.loadResponse(response, response.length, 200, LichessModel.DAILY));
        assertEquals(1913, model.rating());
        assertEquals(LichessModel.MOVE_SOLVED, model.play(52, 36));
    }

    @Test
    void correctPlayerMoveAppliesTheAutomaticReply() {
        LichessModel model = new LichessModel();
        byte[] fen = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1".getBytes();
        int[] moves = {
            PuzzleBoard.parseUci("e2e4".getBytes(), 4),
            PuzzleBoard.parseUci("e8e7".getBytes(), 4),
            PuzzleBoard.parseUci("e4e5".getBytes(), 4)
        };
        model.loadPuzzle(fen, fen.length, moves, moves.length, 1200, 42);

        assertEquals(LichessModel.MOVE_CORRECT, model.play(52, 36));
        assertEquals(PuzzleBoard.PAWN, model.board().pieceAt(36));
        assertEquals(-PuzzleBoard.KING, model.board().pieceAt(12));
        assertEquals(LichessModel.MOVE_SOLVED, model.play(36, 28));
    }

    @Test
    void reportsRemainingPlayerMoves() {
        LichessModel model = new LichessModel();
        byte[] fen = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1".getBytes();
        int[] moves = {
            PuzzleBoard.parseUci("e2e4".getBytes(), 4),
            PuzzleBoard.parseUci("e8e7".getBytes(), 4),
            PuzzleBoard.parseUci("e4e5".getBytes(), 4)
        };
        model.loadPuzzle(fen, fen.length, moves, moves.length, 1200, 42);

        assertEquals(2, model.remainingPlayerMoves());
        model.play(52, 36);
        assertEquals(1, model.remainingPlayerMoves());
        model.play(36, 28);
        assertEquals(0, model.remainingPlayerMoves());
    }

    @Test
    void readsFreshPuzzlePositionFromGamePgn() {
        LichessModel model = new LichessModel();
        byte[] response = ("{\"puzzle\":{\"rating\":1539,\"plays\":2730,"
                + "\"initialPly\":3,\"solution\":[\"f3e5\"]},"
                + "\"game\":{\"pgn\":\"e4 e5 Nf3 Nc6\"}}").getBytes();

        assertEquals(true, model.loadResponse(response, response.length, 200, LichessModel.FRESH));
        assertEquals(1, model.board().playerColor());
        assertEquals(PuzzleBoard.PAWN, model.board().pieceAt(36));
        assertEquals(-PuzzleBoard.PAWN, model.board().pieceAt(28));
        assertEquals(LichessModel.MOVE_SOLVED, model.play(45, 28));
    }

    @Test
    void readsARealFreshPuzzlePgnWithCapturesAndCastling() {
        LichessModel model = new LichessModel();
        byte[] response = ("{\"game\":{\"pgn\":\"e4 c5 Bc4 Nc6 Nc3 g6 d3 Bg7 Bd2 d6 a3 Nf6 Nf3 O-O h3 Nd7 O-O Nce5 "
                + "Ba2 Nxf3+ Qxf3 Ne5 Qg3 Be6 Rad1 Bxa2 Nxa2 Qb6 Bc3 a6 Nc1 Qc7 f4 Nc6 Bxg7 Kxg7 f5 Nd4 "
                + "fxg6 fxg6 Rxf8 Rxf8 Rf1 Nxc2\"},\"puzzle\":{\"rating\":1298,\"plays\":2727,"
                + "\"initialPly\":43,\"solution\":[\"f1f8\",\"g7f8\",\"g3f2\"]}}").getBytes();

        assertEquals(true, model.loadResponse(response, response.length, 200, LichessModel.FRESH));
        assertEquals(1, model.playerColor());
        assertEquals(LichessModel.MOVE_CORRECT, model.play(61, 5));
        assertEquals(-PuzzleBoard.KING, model.board().pieceAt(5));
    }

    @Test
    void wrongMoveDoesNotChangeThePosition() {
        LichessModel model = new LichessModel();
        byte[] fen = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1".getBytes();
        int[] moves = {PuzzleBoard.parseUci("e2e4".getBytes(), 4)};
        model.loadPuzzle(fen, fen.length, moves, 1, 1200, 42);

        assertEquals(LichessModel.MOVE_WRONG, model.play(52, 44));
        assertEquals(PuzzleBoard.PAWN, model.board().pieceAt(52));
    }

    @Test
    void solvedDailyPuzzleOpensTheWinView() {
        LichessModel model = new LichessModel();
        byte[] fen = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1".getBytes();
        int[] moves = {PuzzleBoard.parseUci("e2e4".getBytes(), 4)};
        model.loadPuzzle(fen, fen.length, moves, moves.length, 1200, 42);
        Navigator navigator = mock(Navigator.class);
        Clock clock = mock(Clock.class);
        ChallengeView view = new ChallengeView(navigator, model, clock);

        view.enter();
        view.touch(134, 226);
        view.touch(134, 170);

        verify(navigator).show(LichessViews.WIN);
    }

    @Test
    void solvedFreshPuzzleOpensTheWinView() {
        LichessModel model = new LichessModel();
        byte[] response = ("{\"game\":{\"pgn\":\"e4 e5 Nf3 Nc6\"},"
                + "\"puzzle\":{\"rating\":1539,\"plays\":2730,"
                + "\"initialPly\":3,\"solution\":[\"f3e5\"]}}").getBytes();
        model.loadResponse(response, response.length, 200, LichessModel.FRESH);
        Navigator navigator = mock(Navigator.class);
        Clock clock = mock(Clock.class);
        ChallengeView view = new ChallengeView(navigator, model, clock);

        view.enter();
        view.touch(162, 198);
        view.touch(134, 142);

        verify(navigator).show(LichessViews.WIN);
    }
}
