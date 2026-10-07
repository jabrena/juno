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

final class ChessEngine {
    private ChessEngine() {
    }

    static boolean isLegal(byte[] board, int[] moves, int from, int to) {
        int count = generateMoves(board, moves, 0);
        for (int i = 0; i < count; i++) {
            int move = moves[i];
            if ((move & 63) == from && ((move >> 6) & 63) == to && isLegalMove(board, move)) {
                return true;
            }
        }
        return false;
    }

    static int countLegalMoves(byte[] board, int[] moves) {
        int count = generateMoves(board, moves, 0);
        int legal = 0;
        for (int i = 0; i < count; i++) {
            if (isLegalMove(board, moves[i])) {
                legal = legal + 1;
            }
        }
        return legal;
    }

    static boolean isLegalMove(byte[] board, int move) {
        int undo = makeMove(board, move);
        boolean legal = !inCheck(board, -side);
        unmakeMove(board, move, undo);
        return legal;
    }

    static int negamax(byte[] board, int[] moves, int depth, int ply, int alpha, int beta) {
        if (depth == 0) {
            return evaluate(board) * side;
        }
        int base = ply * MOVES_PER_PLY;
        int count = generateMoves(board, moves, base);
        if (ply == 0) {
            shuffle(moves, base, count);
        }
        orderCapturesFirst(board, moves, base, count);

        int legal = 0;
        for (int i = 0; i < count; i++) {
            int move = moves[base + i];
            int undo = makeMove(board, move);
            if (inCheck(board, -side)) {
                unmakeMove(board, move, undo);
                continue;
            }
            legal = legal + 1;
            int score = -negamax(board, moves, depth - 1, ply + 1, -beta, -alpha);
            unmakeMove(board, move, undo);
            if (score > alpha) {
                alpha = score;
                if (ply == 0) {
                    bestMove = move;
                }
            }
            if (alpha >= beta) {
                return alpha;
            }
        }
        if (legal == 0) {
            if (inCheck(board, side)) {
                return -MATE + ply;
            }
            return 0;
        }
        return alpha;
    }

    static void shuffle(int[] moves, int base, int count) {
        for (int i = count - 1; i > 0; i--) {
            int j = Random.nextInt(i + 1);
            int swap = moves[base + i];
            moves[base + i] = moves[base + j];
            moves[base + j] = swap;
        }
    }

    static void orderCapturesFirst(byte[] board, int[] moves, int base, int count) {
        int next = 0;
        for (int i = 0; i < count; i++) {
            int move = moves[base + i];
            if (board[(move >> 6) & 63] != 0) {
                for (int j = i; j > next; j--) {
                    moves[base + j] = moves[base + j - 1];
                }
                moves[base + next] = move;
                next = next + 1;
            }
        }
    }

    static int evaluate(byte[] board) {
        int score = 0;
        for (int square = 0; square < 64; square++) {
            int piece = board[square];
            if (piece == 0) {
                continue;
            }
            int type = Math.abs(piece);
            int row = square >> 3;
            int column = square & 7;
            int center = 7 - Math.max(Math.abs(2 * column - 7), Math.abs(2 * row - 7));
            int value = pieceValue(type);
            if (type == PAWN) {
                int advance = 6 - row;
                if (piece < 0) {
                    advance = row - 1;
                }
                value = value + advance * 6 + center * 2;
            } else if (type == KNIGHT) {
                value = value + center * 5;
            } else if (type == BISHOP) {
                value = value + center * 3;
            } else if (type == QUEEN) {
                value = value + center;
            } else if (type == KING) {
                value = value - center * 3;
            }
            if (piece > 0) {
                score = score + value;
            } else {
                score = score - value;
            }
        }
        return score;
    }

    static int pieceValue(int type) {
        if (type == PAWN) {
            return 100;
        }
        if (type == KNIGHT) {
            return 320;
        }
        if (type == BISHOP) {
            return 330;
        }
        if (type == ROOK) {
            return 500;
        }
        if (type == QUEEN) {
            return 900;
        }
        return 0;
    }
}
