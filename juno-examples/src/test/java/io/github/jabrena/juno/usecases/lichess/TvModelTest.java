package io.github.jabrena.juno.usecases.lichess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TvModelTest {
    @Test
    void readsTheLatestPositionFromTheTvFeed() {
        TvModel model = new TvModel();
        byte[] response = ("{\"t\":\"featured\",\"d\":{\"orientation\":\"black\",\"fen\":\"8/8/8/8/8/8/4P3/4K3 w - - 0 1\"}}\n"
                + "{\"t\":\"fen\",\"d\":{\"fen\":\"8/8/8/8/8/8/8/4K2k b - - 0 1\",\"lm\":\"e2e4\",\"wc\":51,\"bc\":53}}\n").getBytes();

        assertTrue(model.loadResponse(response, response.length, 200));
        assertEquals(-1, model.orientation());
        assertEquals(-PuzzleBoard.KING, model.board().pieceAt(63));
        assertEquals(51, model.whiteSeconds());
        assertEquals(53, model.blackSeconds());
        assertEquals(52, model.lastFrom());
        assertEquals(36, model.lastTo());
    }
}
