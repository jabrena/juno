package io.github.jabrena.juno.games.chess;

import static io.github.jabrena.juno.games.chess.BishopSprite.bishopDetail;
import static io.github.jabrena.juno.games.chess.BishopSprite.bishopShape;
import static io.github.jabrena.juno.games.chess.Chess.BISHOP;
import static io.github.jabrena.juno.games.chess.Chess.KNIGHT;
import static io.github.jabrena.juno.games.chess.Chess.PAWN;
import static io.github.jabrena.juno.games.chess.Chess.QUEEN;
import static io.github.jabrena.juno.games.chess.Chess.ROOK;
import static io.github.jabrena.juno.games.chess.KingSprite.kingDetail;
import static io.github.jabrena.juno.games.chess.KingSprite.kingShape;
import static io.github.jabrena.juno.games.chess.KnightSprite.knightDetail;
import static io.github.jabrena.juno.games.chess.KnightSprite.knightShape;
import static io.github.jabrena.juno.games.chess.PawnSprite.pawnDetail;
import static io.github.jabrena.juno.games.chess.PawnSprite.pawnShape;
import static io.github.jabrena.juno.games.chess.QueenSprite.queenDetail;
import static io.github.jabrena.juno.games.chess.QueenSprite.queenShape;
import static io.github.jabrena.juno.games.chess.RookSprite.rookDetail;
import static io.github.jabrena.juno.games.chess.RookSprite.rookShape;

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
