package io.github.jabrena.juno.usecases.lichess;

/** The latest position received from the public Lichess TV ND-JSON feed. */
final class TvModel {
    private final PuzzleBoard board = new PuzzleBoard();
    private final byte[] fen = new byte[96];
    private final byte[] moveBuffer = new byte[5];
    private int orientation = 1;
    private int whiteSeconds;
    private int blackSeconds;
    private int lastFrom = -1;
    private int lastTo = -1;
    private int version;

    boolean loadResponse(byte[] response, int length, int statusCode) {
        if (response == null || length <= 0 || length > response.length || statusCode != 200) {
            return false;
        }
        boolean loaded = false;
        int cursor = 0;
        while (cursor < length) {
            int end = cursor;
            while (end < length && response[end] != '\n') {
                end++;
            }
            int lineEnd = end > cursor && response[end - 1] == '\r' ? end - 1 : end;
            int featured = find(response, cursor, lineEnd, "\"t\":\"featured\"");
            if (featured >= 0) {
                orientation = find(response, cursor, lineEnd, "\"orientation\":\"black\"") >= 0 ? -1 : 1;
            }
            int value = find(response, cursor, lineEnd, "\"fen\":\"");
            if (value >= 0) {
                int start = value + 7;
                int finish = start;
                while (finish < lineEnd && response[finish] != '"') {
                    finish++;
                }
                int count = finish - start;
                if (count > 0 && count <= fen.length) {
                    copy(response, start, fen, count);
                    if (board.loadFen(fen, count)) {
                        parseMove(response, cursor, lineEnd);
                        whiteSeconds = readNumber(response, cursor, lineEnd, "\"wc\":", whiteSeconds);
                        blackSeconds = readNumber(response, cursor, lineEnd, "\"bc\":", blackSeconds);
                        loaded = true;
                    }
                }
            }
            cursor = end == length ? length : end + 1;
        }
        if (loaded) {
            version++;
        }
        return loaded;
    }

    PuzzleBoard board() {
        return board;
    }

    int orientation() {
        return orientation;
    }

    int whiteSeconds() {
        return whiteSeconds;
    }

    int blackSeconds() {
        return blackSeconds;
    }

    int lastFrom() {
        return lastFrom;
    }

    int lastTo() {
        return lastTo;
    }

    int version() {
        return version;
    }

    private void parseMove(byte[] response, int start, int end) {
        int value = find(response, start, end, "\"lm\":\"");
        if (value < 0) {
            lastFrom = -1;
            lastTo = -1;
            return;
        }
        int moveStart = value + 6;
        int moveEnd = moveStart;
        while (moveEnd < end && response[moveEnd] != '"') {
            moveEnd++;
        }
        int moveLength = moveEnd - moveStart;
        if (moveLength > moveBuffer.length) {
            lastFrom = -1;
            lastTo = -1;
            return;
        }
        copy(response, moveStart, moveBuffer, moveLength);
        int move = PuzzleBoard.parseUci(moveBuffer, moveLength);
        if (move < 0) {
            lastFrom = -1;
            lastTo = -1;
        } else {
            lastFrom = move & 63;
            lastTo = (move >> 6) & 63;
        }
    }

    private static int readNumber(byte[] input, int start, int end, String key, int fallback) {
        int value = find(input, start, end, key);
        if (value < 0) {
            return fallback;
        }
        int cursor = value + key.length();
        while (cursor < end && (input[cursor] == ' ' || input[cursor] == '\t')) {
            cursor++;
        }
        int result = 0;
        int digits = 0;
        while (cursor < end && input[cursor] >= '0' && input[cursor] <= '9') {
            result = result * 10 + input[cursor] - '0';
            cursor++;
            digits++;
        }
        return digits == 0 ? fallback : result;
    }

    private static int find(byte[] input, int start, int end, String value) {
        int limit = end - value.length();
        for (int index = start; index <= limit; index++) {
            int offset = 0;
            while (offset < value.length() && input[index + offset] == value.charAt(offset)) {
                offset++;
            }
            if (offset == value.length()) {
                return index;
            }
        }
        return -1;
    }

    private static void copy(byte[] source, int sourceStart, byte[] target, int length) {
        for (int index = 0; index < length; index++) {
            target[index] = source[sourceStart + index];
        }
    }
}
