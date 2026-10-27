package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.hardware.net.http.Json;

/** The MVC model: one downloaded Lichess puzzle and the bounded state of the current attempt. */
public final class LichessModel {
    public static final int DAILY = 0;
    public static final int FRESH = 1;
    public static final int LOAD_OK = 0;
    public static final int LOAD_NETWORK_ERROR = 1;
    public static final int LOAD_BAD_RESPONSE = 2;
    public static final int MOVE_WRONG = 0;
    public static final int MOVE_CORRECT = 1;
    public static final int MOVE_SOLVED = 2;
    private static final int MAX_SOLUTION_MOVES = 16;

    private final PuzzleBoard board = new PuzzleBoard();
    private final int[] solution = new int[MAX_SOLUTION_MOVES];
    private final byte[] fen = new byte[96];
    private final byte[] pgn = new byte[1024];
    private final byte[] uci = new byte[8];
    private final byte[] theme = new byte[24];
    private int solutionCount;
    private int solutionIndex;
    private int rating;
    private int plays;
    private int themeLength;
    private int source = DAILY;
    private int loadError = LOAD_BAD_RESPONSE;
    private int lastFrom = -1;
    private int lastTo = -1;
    private int replyFrom = -1;
    private int replyTo = -1;
    private boolean loaded;

    /** Loads a JSON response produced by {@code /api/puzzle/daily} or {@code /api/puzzle/next}. */
    public boolean loadResponse(byte[] response, int responseLength, int status, int source) {
        if (status != 200 || response == null || responseLength <= 0) {
            loadError = LOAD_NETWORK_ERROR;
            loaded = false;
            return false;
        }
        int fenLength = Json.getString(response, responseLength, "puzzle.fen", fen, fen.length);
        boolean positionLoaded = fenLength > 0 && board.loadFen(fen, fenLength);
        if (!positionLoaded) {
            int pgnLength = Json.getString(response, responseLength, "game.pgn", pgn, pgn.length);
            int initialPly = Json.getInt(response, responseLength, "puzzle.initialPly");
            positionLoaded = pgnLength > 0 && initialPly < Integer.MAX_VALUE
                    && board.loadPgnPosition(pgn, pgnLength, initialPly + 1);
        }
        int count = Json.arraySize(response, responseLength, "puzzle.solution");
        if (!positionLoaded || count <= 0 || count > solution.length) {
            loadError = LOAD_BAD_RESPONSE;
            loaded = false;
            return false;
        }
        int parsed = 0;
        for (; parsed < count; parsed++) {
            int uciLength = Json.getString(response, responseLength, solutionPath(parsed), uci, uci.length);
            solution[parsed] = PuzzleBoard.parseUci(uci, uciLength);
            if (solution[parsed] < 0) {
                loadError = LOAD_BAD_RESPONSE;
                loaded = false;
                return false;
            }
        }
        solutionCount = parsed;
        rating = Json.getInt(response, responseLength, "puzzle.rating");
        plays = Json.getInt(response, responseLength, "puzzle.plays");
        themeLength = Json.getString(response, responseLength, "puzzle.themes[0]", theme, theme.length);
        this.source = source;
        loadError = LOAD_OK;
        loaded = true;
        resetPuzzle();
        return true;
    }

    /** Package-private fixture loader used by the model tests. */
    void loadPuzzle(byte[] position, int positionLength, int[] moves, int count, int rating, int plays) {
        board.loadFen(position, positionLength);
        for (int index = 0; index < count; index++) {
            solution[index] = moves[index];
        }
        solutionCount = count;
        this.rating = rating;
        this.plays = plays;
        themeLength = 0;
        source = DAILY;
        loadError = LOAD_OK;
        loaded = true;
        resetPuzzle();
    }

    public void resetPuzzle() {
        board.reset();
        solutionIndex = 0;
        lastFrom = -1;
        lastTo = -1;
        replyFrom = -1;
        replyTo = -1;
    }

    /** Validates the selected move and automatically plays Lichess's reply when the move is correct. */
    public int play(int from, int to) {
        if (!loaded || solutionIndex >= solutionCount) {
            return MOVE_WRONG;
        }
        int expected = solution[solutionIndex];
        if (PuzzleBoard.from(expected) != from || PuzzleBoard.to(expected) != to) {
            return MOVE_WRONG;
        }
        board.apply(expected);
        lastFrom = from;
        lastTo = to;
        replyFrom = -1;
        replyTo = -1;
        solutionIndex++;
        if (solutionIndex >= solutionCount) {
            return MOVE_SOLVED;
        }
        int reply = solution[solutionIndex];
        replyFrom = PuzzleBoard.from(reply);
        replyTo = PuzzleBoard.to(reply);
        board.apply(reply);
        solutionIndex++;
        return solutionIndex >= solutionCount ? MOVE_SOLVED : MOVE_CORRECT;
    }

    public PuzzleBoard board() {
        return board;
    }

    public int playerColor() {
        return board.playerColor();
    }

    public int rating() {
        return rating;
    }

    public int plays() {
        return plays;
    }

    public byte[] theme() {
        return theme;
    }

    public int themeLength() {
        return themeLength;
    }

    public int source() {
        return source;
    }

    public int loadError() {
        return loadError;
    }

    public int lastFrom() {
        return lastFrom;
    }

    public int lastTo() {
        return lastTo;
    }

    public int replyFrom() {
        return replyFrom;
    }

    public int replyTo() {
        return replyTo;
    }

    private static String solutionPath(int index) {
        return switch (index) {
            case 0 -> "puzzle.solution[0]";
            case 1 -> "puzzle.solution[1]";
            case 2 -> "puzzle.solution[2]";
            case 3 -> "puzzle.solution[3]";
            case 4 -> "puzzle.solution[4]";
            case 5 -> "puzzle.solution[5]";
            case 6 -> "puzzle.solution[6]";
            case 7 -> "puzzle.solution[7]";
            case 8 -> "puzzle.solution[8]";
            case 9 -> "puzzle.solution[9]";
            case 10 -> "puzzle.solution[10]";
            case 11 -> "puzzle.solution[11]";
            case 12 -> "puzzle.solution[12]";
            case 13 -> "puzzle.solution[13]";
            case 14 -> "puzzle.solution[14]";
            default -> "puzzle.solution[15]";
        };
    }
}
