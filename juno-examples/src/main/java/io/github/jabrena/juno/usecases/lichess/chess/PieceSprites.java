package io.github.jabrena.juno.usecases.lichess.chess;

import static io.github.jabrena.juno.usecases.lichess.chess.BishopSprite.bishopDetail;
import static io.github.jabrena.juno.usecases.lichess.chess.BishopSprite.bishopShape;
import static io.github.jabrena.juno.usecases.lichess.chess.ChessPieces.BISHOP;
import static io.github.jabrena.juno.usecases.lichess.chess.ChessPieces.KNIGHT;
import static io.github.jabrena.juno.usecases.lichess.chess.ChessPieces.PAWN;
import static io.github.jabrena.juno.usecases.lichess.chess.ChessPieces.QUEEN;
import static io.github.jabrena.juno.usecases.lichess.chess.ChessPieces.ROOK;
import static io.github.jabrena.juno.usecases.lichess.chess.KingSprite.kingDetail;
import static io.github.jabrena.juno.usecases.lichess.chess.KingSprite.kingShape;
import static io.github.jabrena.juno.usecases.lichess.chess.KnightSprite.knightDetail;
import static io.github.jabrena.juno.usecases.lichess.chess.KnightSprite.knightShape;
import static io.github.jabrena.juno.usecases.lichess.chess.PawnSprite.pawnDetail;
import static io.github.jabrena.juno.usecases.lichess.chess.PawnSprite.pawnShape;
import static io.github.jabrena.juno.usecases.lichess.chess.QueenSprite.queenDetail;
import static io.github.jabrena.juno.usecases.lichess.chess.QueenSprite.queenShape;
import static io.github.jabrena.juno.usecases.lichess.chess.RookSprite.rookDetail;
import static io.github.jabrena.juno.usecases.lichess.chess.RookSprite.rookShape;

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
