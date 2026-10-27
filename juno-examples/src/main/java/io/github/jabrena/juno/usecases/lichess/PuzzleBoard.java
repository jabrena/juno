package io.github.jabrena.juno.usecases.lichess;

/**
 * The bounded board state used by a Lichess puzzle. Squares are numbered from a8 to h1 and pieces are signed: white
 * pieces are positive, black pieces negative.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class PuzzleBoard {
    static final int EMPTY = 0;
    static final int PAWN = 1;
    static final int KNIGHT = 2;
    static final int BISHOP = 3;
    static final int ROOK = 4;
    static final int QUEEN = 5;
    static final int KING = 6;

    private final byte[] squares = new byte[64];
    private final byte[] initial = new byte[64];
    private int playerColor;
    private int initialPlayerColor;
    private int enPassant = -1;
    private int initialEnPassant = -1;

    /** Loads the FEN position returned by the Lichess puzzle API. */
    public boolean loadFen(byte[] fen, int length) {
        if (fen == null || length <= 0 || length > fen.length) {
            return false;
        }
        clear();
        int cursor = 0;
        int square = 0;
        while (cursor < length && fen[cursor] != ' ') {
            int value = fen[cursor++];
            if (value >= '1' && value <= '8') {
                square = square + value - '0';
            } else if (piece(value) != 0 && square < 64) {
                squares[square++] = (byte) piece(value);
            } else if (value == '/') {
                // The next rank starts at the next square; the FEN rank separator itself has no state.
            } else {
                return false;
            }
        }
        if (square != 64 || cursor + 2 >= length) {
            return false;
        }
        playerColor = fen[cursor + 1] == 'w' ? 1 : fen[cursor + 1] == 'b' ? -1 : 0;
        if (playerColor == 0) {
            return false;
        }
        while (cursor < length && fen[cursor] != ' ') {
            cursor++;
        }
        cursor = skipField(fen, cursor, length);
        cursor = skipField(fen, cursor, length);
        while (cursor < length && fen[cursor] == ' ') {
            cursor++;
        }
        int enPassantFile = -1;
        int enPassantRank = -1;
        if (cursor + 1 < length && fen[cursor] >= 'a' && fen[cursor] <= 'h'
                && fen[cursor + 1] >= '1' && fen[cursor + 1] <= '8') {
            enPassantFile = fen[cursor] - 'a';
            enPassantRank = '8' - fen[cursor + 1];
        }
        enPassant = enPassantRank < 0 ? -1 : enPassantRank * 8 + enPassantFile;
        copy(squares, initial);
        initialPlayerColor = playerColor;
        initialEnPassant = enPassant;
        return true;
    }

    /** Reconstructs the fresh-puzzle position from the PGN and the API's initial ply. */
    public boolean loadPgnPosition(byte[] pgn, int length, int initialPly) {
        if (pgn == null || length <= 0 || length > pgn.length || initialPly < 0) {
            return false;
        }
        setupInitialPosition();
        int cursor = 0;
        int ply = 0;
        int color = 1;
        while (cursor < length && ply < initialPly) {
            cursor = skipWhitespace(pgn, cursor, length);
            if (cursor >= length) {
                break;
            }
            if (pgn[cursor] == '{') {
                cursor = skipComment(pgn, cursor, length);
                continue;
            }
            int start = cursor;
            while (cursor < length && pgn[cursor] > ' ') {
                cursor++;
            }
            if (isIgnoredToken(pgn, start, cursor)) {
                continue;
            }
            if (!applySan(pgn, start, cursor, color)) {
                return false;
            }
            color = -color;
            ply++;
        }
        if (ply != initialPly) {
            return false;
        }
        playerColor = color;
        copy(squares, initial);
        initialPlayerColor = playerColor;
        initialEnPassant = enPassant;
        return true;
    }

    /** Restores the position before the first puzzle move. */
    public void reset() {
        copy(initial, squares);
        playerColor = initialPlayerColor;
        enPassant = initialEnPassant;
    }

    /** Applies a UCI move, including castling, en passant and promotion. */
    public void apply(int move) {
        int from = from(move);
        int to = to(move);
        int piece = squares[from];
        int type = Math.abs(piece);
        squares[from] = 0;
        if (type == PAWN && to == enPassant && squares[to] == 0) {
            int captured = to + (piece > 0 ? 8 : -8);
            squares[captured] = 0;
        }
        squares[to] = (byte) piece;
        if (type == KING && Math.abs(to - from) == 2) {
            int rookFrom = to > from ? from + 3 : from - 4;
            int rookTo = to > from ? from + 1 : from - 1;
            squares[rookTo] = squares[rookFrom];
            squares[rookFrom] = 0;
        }
        int promotion = promotion(move);
        if (promotion != 0) {
            squares[to] = (byte) (piece > 0 ? promotion : -promotion);
        }
        enPassant = type == PAWN && Math.abs(to - from) == 16 ? (from + to) / 2 : -1;
    }

    /** Encodes a four- or five-character UCI move, or returns {@code -1} when it is malformed. */
    public static int parseUci(byte[] uci, int length) {
        if (uci == null || (length != 4 && length != 5) || length > uci.length) {
            return -1;
        }
        int from = square(uci[0], uci[1]);
        int to = square(uci[2], uci[3]);
        if (from < 0 || to < 0) {
            return -1;
        }
        int promotion = 0;
        if (length == 5) {
            promotion = promotion(uci[4]);
            if (promotion == 0) {
                return -1;
            }
        }
        return from | to << 6 | promotion << 12;
    }

    public int pieceAt(int square) {
        return squares[square];
    }

    public int playerColor() {
        return playerColor;
    }

    static int from(int move) {
        return move & 63;
    }

    static int to(int move) {
        return move >> 6 & 63;
    }

    private static int promotion(int move) {
        return move >> 12 & 7;
    }

    private void clear() {
        for (int index = 0; index < squares.length; index++) {
            squares[index] = 0;
        }
        enPassant = -1;
    }

    private void setupInitialPosition() {
        clear();
        for (int column = 0; column < 8; column++) {
            squares[8 + column] = (byte) -PAWN;
            squares[48 + column] = (byte) PAWN;
            int piece = backRankPiece(column);
            squares[column] = (byte) -piece;
            squares[56 + column] = (byte) piece;
        }
        playerColor = 1;
        enPassant = -1;
    }

    private boolean applySan(byte[] pgn, int start, int end, int color) {
        if (isCastle(pgn, start, end, 'O') || isCastle(pgn, start, end, '0')) {
            int from = color > 0 ? 60 : 4;
            applyMove(from, from + 2, 0);
            return true;
        }
        if (isLongCastle(pgn, start, end, 'O') || isLongCastle(pgn, start, end, '0')) {
            int from = color > 0 ? 60 : 4;
            applyMove(from, from - 2, 0);
            return true;
        }

        int destination = -1;
        int destinationIndex = -1;
        for (int index = end - 1; index > start; index--) {
            if (pgn[index] >= '1' && pgn[index] <= '8' && pgn[index - 1] >= 'a' && pgn[index - 1] <= 'h') {
                destination = ('8' - pgn[index]) * 8 + pgn[index - 1] - 'a';
                destinationIndex = index - 1;
                break;
            }
        }
        if (destination < 0) {
            return false;
        }

        int cursor = start;
        int type = pieceType(pgn[cursor]);
        if (type != 0) {
            cursor++;
        } else {
            type = PAWN;
        }
        boolean capture = false;
        int disambiguationFile = -1;
        int disambiguationRank = -1;
        for (int index = cursor; index < destinationIndex; index++) {
            byte value = pgn[index];
            if (value == 'x') {
                capture = true;
            } else if (value >= 'a' && value <= 'h') {
                disambiguationFile = value - 'a';
            } else if (value >= '1' && value <= '8') {
                disambiguationRank = '8' - value;
            }
        }
        int promotion = sanPromotion(pgn, destinationIndex + 2, end);
        for (int from = 0; from < 64; from++) {
            int piece = squares[from];
            if (piece != color * type || (disambiguationFile >= 0 && (from & 7) != disambiguationFile)
                    || (disambiguationRank >= 0 && (from >> 3) != disambiguationRank)) {
                continue;
            }
            if (canMove(type, from, destination, color, capture)) {
                applyMove(from, destination, promotion);
                return true;
            }
        }
        return false;
    }

    private boolean canMove(int type, int from, int to, int color, boolean capture) {
        int fromRow = from >> 3;
        int fromColumn = from & 7;
        int toRow = to >> 3;
        int toColumn = to & 7;
        int target = squares[to];
        if (target * color > 0) {
            return false;
        }
        int rowDelta = toRow - fromRow;
        int columnDelta = toColumn - fromColumn;
        if (type == PAWN) {
            int direction = color > 0 ? -1 : 1;
            int startRow = color > 0 ? 6 : 1;
            if (columnDelta == 0 && !capture && target == 0 && rowDelta == direction) {
                return true;
            }
            if (columnDelta == 0 && !capture && target == 0 && fromRow == startRow && rowDelta == 2 * direction
                    && squares[from + direction * 8] == 0) {
                return true;
            }
            return Math.abs(columnDelta) == 1 && rowDelta == direction
                    && (target * color < 0 || to == enPassant);
        }
        if (type == KNIGHT) {
            return Math.abs(rowDelta) * Math.abs(columnDelta) == 2;
        }
        if (type == KING) {
            return Math.abs(rowDelta) <= 1 && Math.abs(columnDelta) <= 1;
        }
        boolean diagonal = Math.abs(rowDelta) == Math.abs(columnDelta);
        boolean straight = rowDelta == 0 || columnDelta == 0;
        if ((type == BISHOP && !diagonal) || (type == ROOK && !straight)
                || (type == QUEEN && !diagonal && !straight)) {
            return false;
        }
        int rowStep = rowDelta < 0 ? -1 : rowDelta > 0 ? 1 : 0;
        int columnStep = columnDelta < 0 ? -1 : columnDelta > 0 ? 1 : 0;
        int row = fromRow + rowStep;
        int column = fromColumn + columnStep;
        while (row != toRow || column != toColumn) {
            if (squares[row * 8 + column] != 0) {
                return false;
            }
            row += rowStep;
            column += columnStep;
        }
        return true;
    }

    private void applyMove(int from, int to, int promotion) {
        int piece = squares[from];
        int type = Math.abs(piece);
        squares[from] = 0;
        if (type == PAWN && to == enPassant && squares[to] == 0) {
            int captured = to + (piece > 0 ? 8 : -8);
            squares[captured] = 0;
        }
        squares[to] = (byte) (promotion == 0 ? piece : piece > 0 ? promotion : -promotion);
        if (type == KING && Math.abs(to - from) == 2) {
            int rookFrom = to > from ? from + 3 : from - 4;
            int rookTo = to > from ? from + 1 : from - 1;
            squares[rookTo] = squares[rookFrom];
            squares[rookFrom] = 0;
        }
        enPassant = type == PAWN && Math.abs(to - from) == 16 ? (from + to) / 2 : -1;
    }

    private static int pieceType(byte value) {
        if (value == 'K') {
            return KING;
        }
        if (value == 'Q') {
            return QUEEN;
        }
        if (value == 'R') {
            return ROOK;
        }
        if (value == 'B') {
            return BISHOP;
        }
        return value == 'N' ? KNIGHT : 0;
    }

    private static int sanPromotion(byte[] pgn, int start, int end) {
        for (int index = start; index + 1 < end; index++) {
            if (pgn[index] == '=') {
                byte value = pgn[index + 1];
                return value == 'Q' ? QUEEN : value == 'R' ? ROOK : value == 'B' ? BISHOP
                        : value == 'N' ? KNIGHT : 0;
            }
        }
        return 0;
    }

    private static boolean isCastle(byte[] pgn, int start, int end, int first) {
        return end - start == 3 && pgn[start] == first && pgn[start + 1] == '-' && pgn[start + 2] == first;
    }

    private static boolean isLongCastle(byte[] pgn, int start, int end, int first) {
        return end - start == 5 && pgn[start] == first && pgn[start + 1] == '-'
                && pgn[start + 2] == first && pgn[start + 3] == '-' && pgn[start + 4] == first;
    }

    private static int skipWhitespace(byte[] pgn, int cursor, int length) {
        while (cursor < length && pgn[cursor] <= ' ') {
            cursor++;
        }
        return cursor;
    }

    private static int skipComment(byte[] pgn, int cursor, int length) {
        while (cursor < length && pgn[cursor++] != '}') {
            // Skip bounded PGN comments.
        }
        return cursor;
    }

    private static boolean isIgnoredToken(byte[] pgn, int start, int end) {
        if (end - start == 3 && pgn[start] == 'e' && pgn[start + 1] == '.' && pgn[start + 2] == 'p') {
            return true;
        }
        if ((end - start == 3 && pgn[start] == '1' && pgn[start + 1] == '-' && pgn[start + 2] == '0')
                || (end - start == 3 && pgn[start] == '0' && pgn[start + 1] == '-' && pgn[start + 2] == '1')
                || (end - start == 7 && pgn[start] == '1' && pgn[start + 1] == '/' && pgn[start + 2] == '2')) {
            return true;
        }
        for (int index = start; index < end; index++) {
            if (pgn[index] != '.' && (pgn[index] < '0' || pgn[index] > '9')) {
                return false;
            }
        }
        return start < end;
    }

    private static int backRankPiece(int column) {
        if (column == 0 || column == 7) {
            return ROOK;
        }
        if (column == 1 || column == 6) {
            return KNIGHT;
        }
        if (column == 2 || column == 5) {
            return BISHOP;
        }
        return column == 3 ? QUEEN : KING;
    }

    private static void copy(byte[] source, byte[] target) {
        for (int index = 0; index < source.length; index++) {
            target[index] = source[index];
        }
    }

    private static int skipField(byte[] text, int cursor, int length) {
        while (cursor < length && text[cursor] == ' ') {
            cursor++;
        }
        while (cursor < length && text[cursor] != ' ') {
            cursor++;
        }
        return cursor;
    }

    private static int square(byte file, byte rank) {
        if (file < 'a' || file > 'h' || rank < '1' || rank > '8') {
            return -1;
        }
        return ('8' - rank) * 8 + file - 'a';
    }

    private static int piece(int value) {
        int sign = value >= 'A' && value <= 'Z' ? 1 : -1;
        int lower = value | 32;
        int type = lower == 'p' ? PAWN : lower == 'n' ? KNIGHT : lower == 'b' ? BISHOP
                : lower == 'r' ? ROOK : lower == 'q' ? QUEEN : lower == 'k' ? KING : 0;
        return sign * type;
    }

    private static int promotion(byte value) {
        return value == 'q' ? QUEEN : value == 'r' ? ROOK : value == 'b' ? BISHOP : value == 'n' ? KNIGHT : 0;
    }
}
