package io.github.jabrena.juno.games.chess;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.serial.Serial;
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

final class KnightSprite {
    private KnightSprite() {
    }

    static int knightShape(int row) {
        if (row == 1) {
            return 0x00D00;
        }
        if (row == 2) {
            return 0x01F80;
        }
        if (row == 3) {
            return 0x03FC0;
        }
        if (row == 4) {
            return 0x07FE0;
        }
        if (row == 5) {
            return 0x07FF0;
        }
        if (row == 6) {
            return 0x07FF8;
        }
        if (row == 7) {
            return 0x07FFC;
        }
        if (row == 8) {
            return 0x07FFC;
        }
        if (row == 9) {
            return 0x07F78;
        }
        if (row == 10) {
            return 0x0FF80;
        }
        if (row == 11) {
            return 0x0FFC0;
        }
        if (row == 12) {
            return 0x0FFE0;
        }
        if (row == 13) {
            return 0x0FFE0;
        }
        if (row == 14) {
            return 0x0FFE0;
        }
        if (row == 15) {
            return 0x0FFF0;
        }
        if (row == 16) {
            return 0x1FFF8;
        }
        if (row == 17) {
            return 0x1FFF8;
        }
        return 0;
    }

    static int knightDetail(int row) {
        if (row == 4) {
            return 0x00100;
        }
        if (row == 8) {
            return 0x00004;
        }
        return 0;
    }
}
