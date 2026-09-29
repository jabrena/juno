package io.github.jabrena.juno.games.chess;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.usb.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import static io.github.jabrena.juno.games.chess.Chess.*;
import static io.github.jabrena.juno.games.chess.ChessEngine.*;
import static io.github.jabrena.juno.games.chess.ChessRules.*;
import static io.github.jabrena.juno.games.chess.Controls.*;
import static io.github.jabrena.juno.games.chess.SceneRenderer.*;
import static io.github.jabrena.juno.games.chess.PieceSprites.*;
import static io.github.jabrena.juno.games.chess.PawnSprite.*;
import static io.github.jabrena.juno.games.chess.KnightSprite.*;
import static io.github.jabrena.juno.games.chess.BishopSprite.*;
import static io.github.jabrena.juno.games.chess.RookSprite.*;
import static io.github.jabrena.juno.games.chess.QueenSprite.*;
import static io.github.jabrena.juno.games.chess.KingSprite.*;

final class BishopSprite {
    private BishopSprite() {
    }

    static int bishopShape(int row) {
        if (row == 1) {
            return 0x00600;
        }
        if (row == 2) {
            return 0x00F00;
        }
        if (row == 3) {
            return 0x00600;
        }
        if (row == 4) {
            return 0x00F00;
        }
        if (row == 5) {
            return 0x01F80;
        }
        if (row == 6) {
            return 0x03FC0;
        }
        if (row == 7) {
            return 0x03FC0;
        }
        if (row == 8) {
            return 0x03FC0;
        }
        if (row == 9) {
            return 0x01F80;
        }
        if (row == 10) {
            return 0x00F00;
        }
        if (row == 11) {
            return 0x01F80;
        }
        if (row == 12) {
            return 0x00F00;
        }
        if (row == 13) {
            return 0x00F00;
        }
        if (row == 14) {
            return 0x01F80;
        }
        if (row == 15) {
            return 0x07FE0;
        }
        if (row == 16) {
            return 0x1FFF8;
        }
        if (row == 17) {
            return 0x1FFF8;
        }
        return 0;
    }

    static int bishopDetail(int row) {
        if (row == 5) {
            return 0x01000;
        }
        if (row == 6) {
            return 0x00800;
        }
        if (row == 7) {
            return 0x00400;
        }
        if (row == 11) {
            return 0x01F80;
        }
        return 0;
    }
}
