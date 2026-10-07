package io.github.jabrena.juno.games.chess;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import static io.github.jabrena.juno.games.chess.Chess.*;
import static io.github.jabrena.juno.games.chess.ChessEngine.*;
import static io.github.jabrena.juno.games.chess.ChessRules.*;
import static io.github.jabrena.juno.games.chess.MoveGenerator.*;
import static io.github.jabrena.juno.games.chess.Controls.*;
import static io.github.jabrena.juno.games.chess.SceneRenderer.*;
import static io.github.jabrena.juno.games.chess.PieceSprites.*;
import static io.github.jabrena.juno.games.chess.PawnSprite.*;
import static io.github.jabrena.juno.games.chess.KnightSprite.*;
import static io.github.jabrena.juno.games.chess.BishopSprite.*;
import static io.github.jabrena.juno.games.chess.RookSprite.*;
import static io.github.jabrena.juno.games.chess.QueenSprite.*;
import static io.github.jabrena.juno.games.chess.KingSprite.*;

final class ChessRules {
    private ChessRules() {
    }











    static boolean inCheck(byte[] board, int color) {
        int king = whiteKing;
        if (color == BLACK) {
            king = blackKing;
        }
        return isAttacked(board, king, -color);
    }

    static boolean isAttacked(byte[] board, int square, int by) {
        int row = square >> 3;
        int column = square & 7;
        return pawnAttacks(board, row, column, by)
                || knightAttacks(board, row, column, by)
                || kingOrSliderAttacks(board, row, column, by);
    }

    private static boolean pawnAttacks(byte[] board, int row, int column, int by) {
        // A pawn attacks towards the opponent, so an attacking pawn sits one row "behind" the square.
        int pawnRow = row + by;
        if (pawnRow >= 0 && pawnRow <= 7) {
            if (column > 0 && board[pawnRow * 8 + column - 1] == PAWN * by) {
                return true;
            }
            if (column < 7 && board[pawnRow * 8 + column + 1] == PAWN * by) {
                return true;
            }
        }
        return false;
    }

    private static boolean knightAttacks(byte[] board, int row, int column, int by) {
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                if (dr != 0 && dc != 0 && Math.abs(dr) != Math.abs(dc)
                        && pieceAt(board, row + dr, column + dc) == KNIGHT * by) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean kingOrSliderAttacks(byte[] board, int row, int column, int by) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                if (pieceAt(board, row + dr, column + dc) == KING * by) {
                    return true;
                }
                int slider = ROOK;
                if (dr != 0 && dc != 0) {
                    slider = BISHOP;
                }
                int r = row + dr;
                int c = column + dc;
                while (r >= 0 && r <= 7 && c >= 0 && c <= 7) {
                    int piece = board[r * 8 + c];
                    if (piece != 0) {
                        if (piece == slider * by || piece == QUEEN * by) {
                            return true;
                        }
                        break;
                    }
                    r = r + dr;
                    c = c + dc;
                }
            }
        }
        return false;
    }

    static int pieceAt(byte[] board, int row, int column) {
        if (row < 0 || row > 7 || column < 0 || column > 7) {
            return 0;
        }
        return board[row * 8 + column];
    }

    static int makeMove(byte[] board, int move) {
        int from = move & 63;
        int to = (move >> 6) & 63;
        int piece = board[from];
        int captured = board[to];
        int type = Math.abs(piece);
        int undo = (castling << 4) | ((enPassant + 1) << 8);

        board[to] = (byte) piece;
        board[from] = 0;
        int previousEnPassant = enPassant;
        enPassant = -1;
        if (type == PAWN) {
            if (to == previousEnPassant && captured == 0) {
                int capturedSquare = (from & ~7) | (to & 7);
                captured = board[capturedSquare];
                board[capturedSquare] = 0;
                undo = undo | UNDO_EN_PASSANT;
            }
            if (to - from == 16 || from - to == 16) {
                enPassant = (from + to) / 2;
            }
            if (to < 8 || to >= 56) {
                board[to] = (byte) (QUEEN * side);
                undo = undo | UNDO_PROMOTION;
            }
        } else if (type == KING) {
            if (piece > 0) {
                whiteKing = to;
            } else {
                blackKing = to;
            }
            if (to - from == 2) {
                board[from + 1] = board[from + 3];
                board[from + 3] = 0;
                undo = undo | UNDO_CASTLE;
            } else if (from - to == 2) {
                board[from - 1] = board[from - 4];
                board[from - 4] = 0;
                undo = undo | UNDO_CASTLE;
            }
        }
        castling = castling & castlingKept(from) & castlingKept(to);
        side = -side;
        return undo | (captured + 8);
    }

    static int castlingKept(int square) {
        if (square == 60) {
            return ~(WHITE_KING_SIDE | WHITE_QUEEN_SIDE);
        }
        if (square == 63) {
            return ~WHITE_KING_SIDE;
        }
        if (square == 56) {
            return ~WHITE_QUEEN_SIDE;
        }
        if (square == 4) {
            return ~(BLACK_KING_SIDE | BLACK_QUEEN_SIDE);
        }
        if (square == 7) {
            return ~BLACK_KING_SIDE;
        }
        if (square == 0) {
            return ~BLACK_QUEEN_SIDE;
        }
        return ~0;
    }

    static void unmakeMove(byte[] board, int move, int undo) {
        side = -side;
        int from = move & 63;
        int to = (move >> 6) & 63;
        int piece = board[to];
        int captured = (undo & 15) - 8;
        castling = (undo >> 4) & 15;
        enPassant = ((undo >> 8) & 127) - 1;

        if ((undo & UNDO_PROMOTION) != 0) {
            piece = PAWN * side;
        }
        board[from] = (byte) piece;
        if ((undo & UNDO_EN_PASSANT) != 0) {
            board[to] = 0;
            board[(from & ~7) | (to & 7)] = (byte) captured;
        } else {
            board[to] = (byte) captured;
        }
        if (Math.abs(piece) == KING) {
            if (piece > 0) {
                whiteKing = from;
            } else {
                blackKing = from;
            }
            if ((undo & UNDO_CASTLE) != 0) {
                if (to > from) {
                    board[from + 3] = board[from + 1];
                    board[from + 1] = 0;
                } else {
                    board[from - 4] = board[from - 1];
                    board[from - 1] = 0;
                }
            }
        }
    }
}
