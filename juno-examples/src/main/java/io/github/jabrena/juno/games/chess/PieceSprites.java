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

final class PieceSprites {
    private PieceSprites() {
    }

    static int shapeRow(int type, int row) {
        if (type == PAWN) {
            return pawnShape(row);
        }
        if (type == KNIGHT) {
            return knightShape(row);
        }
        if (type == BISHOP) {
            return bishopShape(row);
        }
        if (type == ROOK) {
            return rookShape(row);
        }
        if (type == QUEEN) {
            return queenShape(row);
        }
        return kingShape(row);
    }

    static int detailRow(int type, int row) {
        if (type == PAWN) {
            return pawnDetail(row);
        }
        if (type == KNIGHT) {
            return knightDetail(row);
        }
        if (type == BISHOP) {
            return bishopDetail(row);
        }
        if (type == ROOK) {
            return rookDetail(row);
        }
        if (type == QUEEN) {
            return queenDetail(row);
        }
        return kingDetail(row);
    }
}
