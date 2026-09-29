package io.github.jabrena.juno.games.chess;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.usb.Serial;
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

final class MoveGenerator {
    private MoveGenerator() {
    }

    static int generateMoves(byte[] board, int[] moves, int base) {
        int count = base;
        for (int from = 0; from < 64; from++) {
            int piece = board[from] * side;
            if (piece <= 0) {
                continue;
            }
            int row = from >> 3;
            int column = from & 7;
            if (piece == PAWN) {
                count = pawnMoves(board, moves, count, from, row, column);
            } else if (piece == KNIGHT) {
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        if (dr != 0 && dc != 0 && Math.abs(dr) != Math.abs(dc)) {
                            count = addStep(board, moves, count, from, row + dr, column + dc);
                        }
                    }
                }
            } else if (piece == KING) {
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr != 0 || dc != 0) {
                            count = addStep(board, moves, count, from, row + dr, column + dc);
                        }
                    }
                }
                count = castlingMoves(board, moves, count);
            } else {
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        boolean straight = dr == 0 || dc == 0;
                        if ((dr != 0 || dc != 0)
                                && (piece == QUEEN || (piece == ROOK && straight) || (piece == BISHOP && !straight))) {
                            count = addRay(board, moves, count, from, row, column, dr, dc);
                        }
                    }
                }
            }
        }
        return count - base;
    }

    static int pawnMoves(byte[] board, int[] moves, int count, int from, int row, int column) {
        int direction = -side;
        int next = row + direction;
        if (next < 0 || next > 7) {
            return count;
        }
        if (board[next * 8 + column] == 0) {
            moves[count] = from | ((next * 8 + column) << 6);
            count = count + 1;
            int startRow = 6;
            if (side == BLACK) {
                startRow = 1;
            }
            int jump = (row + 2 * direction) * 8 + column;
            if (row == startRow && board[jump] == 0) {
                moves[count] = from | (jump << 6);
                count = count + 1;
            }
        }
        for (int dc = -1; dc <= 1; dc = dc + 2) {
            int target = column + dc;
            if (target >= 0 && target < 8) {
                int to = next * 8 + target;
                if (board[to] * side < 0 || to == enPassant) {
                    moves[count] = from | (to << 6);
                    count = count + 1;
                }
            }
        }
        return count;
    }

    static int addStep(byte[] board, int[] moves, int count, int from, int row, int column) {
        if (row < 0 || row > 7 || column < 0 || column > 7) {
            return count;
        }
        int to = row * 8 + column;
        if (board[to] * side > 0) {
            return count;
        }
        moves[count] = from | (to << 6);
        return count + 1;
    }

    static int addRay(byte[] board, int[] moves, int count, int from, int row, int column, int dr, int dc) {
        int r = row + dr;
        int c = column + dc;
        while (r >= 0 && r <= 7 && c >= 0 && c <= 7) {
            int to = r * 8 + c;
            int target = board[to] * side;
            if (target > 0) {
                return count;
            }
            moves[count] = from | (to << 6);
            count = count + 1;
            if (target < 0) {
                return count;
            }
            r = r + dr;
            c = c + dc;
        }
        return count;
    }

    static int castlingMoves(byte[] board, int[] moves, int count) {
        int king = 60;
        int kingSide = WHITE_KING_SIDE;
        int queenSide = WHITE_QUEEN_SIDE;
        if (side == BLACK) {
            king = 4;
            kingSide = BLACK_KING_SIDE;
            queenSide = BLACK_QUEEN_SIDE;
        }
        if (board[king] != KING * side || isAttacked(board, king, -side)) {
            return count;
        }
        if ((castling & kingSide) != 0 && board[king + 1] == 0 && board[king + 2] == 0
                && board[king + 3] == ROOK * side
                && !isAttacked(board, king + 1, -side) && !isAttacked(board, king + 2, -side)) {
            moves[count] = king | ((king + 2) << 6);
            count = count + 1;
        }
        if ((castling & queenSide) != 0 && board[king - 1] == 0 && board[king - 2] == 0 && board[king - 3] == 0
                && board[king - 4] == ROOK * side
                && !isAttacked(board, king - 1, -side) && !isAttacked(board, king - 2, -side)) {
            moves[count] = king | ((king - 2) << 6);
            count = count + 1;
        }
        return count;
    }
}
