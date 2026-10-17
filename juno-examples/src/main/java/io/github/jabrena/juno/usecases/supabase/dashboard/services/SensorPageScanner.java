package io.github.jabrena.juno.usecases.supabase.dashboard.services;

import io.github.jabrena.juno.usecases.supabase.dashboard.commons.TemperatureDashboardState;

/**
 * Reads one PostgREST page of {@code sensor-data} rows into the dashboard model in a single pass over caller-owned
 * bytes. Only flat objects whose {@code ts} is a bounded JSON string and whose temperatures are numbers or
 * {@code null} are accepted. The last timestamp becomes the cursor for the next page.
 */
final class SensorPageScanner {
    /** Capacity for one cursor value; a {@code ts} such as {@code 2026-10-17T11:54:50} needs 19 bytes. */
    static final int ID_CAPACITY = 36;

    private static final int ID_FIELD = 3;
    private static final int IGNORED_FIELD = -1;
    private static final int TENTHS_PER_UNIT = 10;

    private final int[] rowTenths = new int[TemperatureDashboardState.SENSORS];
    private final byte[] rowId = new byte[ID_CAPACITY];
    private int rowIdLength;
    private final byte[] lastId = new byte[ID_CAPACITY];
    private int lastIdLength;
    private int parsedTenths;

    byte[] lastId() {
        return lastId;
    }

    int lastIdLength() {
        return lastIdLength;
    }

    /** Adds every complete row in {@code body} to {@code model}; returns the row count, or {@code -1} if malformed. */
    int scan(byte[] body, int length, TemperatureDashboardState model) {
        int rows = 0;
        boolean inRow = false;
        int index = 0;
        while (index < length) {
            int value = body[index];
            if (value == '{') {
                inRow = true;
                startRow();
                index = index + 1;
            } else if (value == '}') {
                if (!inRow || !commitRow(model)) {
                    return -1;
                }
                inRow = false;
                rows = rows + 1;
                index = index + 1;
            } else if (value == '"') {
                index = parseField(body, length, index);
                if (index < 0 || !inRow) {
                    return -1;
                }
            } else {
                index = index + 1;
            }
        }
        return inRow ? -1 : rows;
    }

    private void startRow() {
        rowIdLength = 0;
        int sensor = 0;
        while (sensor < TemperatureDashboardState.SENSORS) {
            rowTenths[sensor] = TemperatureDashboardState.MISSING;
            sensor = sensor + 1;
        }
    }

    private boolean commitRow(TemperatureDashboardState model) {
        if (rowIdLength == 0) {
            return false;
        }
        model.addMedian(rowTenths[0], rowTenths[1], rowTenths[2]);
        int index = 0;
        while (index < rowIdLength) {
            lastId[index] = rowId[index];
            index = index + 1;
        }
        lastIdLength = rowIdLength;
        return true;
    }

    private int parseField(byte[] body, int length, int quote) {
        int keyStart = quote + 1;
        int keyEnd = keyStart;
        while (keyEnd < length && body[keyEnd] != '"') {
            keyEnd = keyEnd + 1;
        }
        if (keyEnd + 1 >= length || body[keyEnd + 1] != ':') {
            return -1;
        }
        int valueStart = keyEnd + 2;
        int field = fieldOf(body, keyStart, keyEnd);
        if (field == ID_FIELD) {
            return parseId(body, length, valueStart);
        }
        int next = parseValue(body, length, valueStart);
        if (next < 0) {
            return -1;
        }
        if (field != IGNORED_FIELD) {
            rowTenths[field] = parsedTenths;
        }
        return next;
    }

    private int parseId(byte[] body, int length, int start) {
        if (start >= length || body[start] != '"') {
            return -1;
        }
        int index = start + 1;
        int count = 0;
        while (index < length && body[index] != '"') {
            if (count >= ID_CAPACITY) {
                return -1;
            }
            rowId[count] = body[index];
            count = count + 1;
            index = index + 1;
        }
        if (index >= length) {
            return -1;
        }
        rowIdLength = count;
        return index + 1;
    }

    private static int fieldOf(byte[] body, int start, int end) {
        if (isKey(body, start, end, "ts")) {
            return ID_FIELD;
        }
        if (isKey(body, start, end, "temp1")) {
            return 0;
        }
        if (isKey(body, start, end, "temp2")) {
            return 1;
        }
        if (isKey(body, start, end, "temp3")) {
            return 2;
        }
        return IGNORED_FIELD;
    }

    private int parseValue(byte[] body, int length, int start) {
        if (matchesAt(body, length, start, "null")) {
            parsedTenths = TemperatureDashboardState.MISSING;
            return start + 4;
        }
        int index = start;
        boolean negative = false;
        if (index < length && body[index] == '-') {
            negative = true;
            index = index + 1;
        }
        int whole = 0;
        int digits = 0;
        while (index < length && isDigit(body[index])) {
            whole = whole * 10 + (body[index] - '0');
            digits = digits + 1;
            index = index + 1;
        }
        if (digits == 0) {
            return -1;
        }
        int fraction = 0;
        if (index < length && body[index] == '.') {
            index = index + 1;
            if (index >= length || !isDigit(body[index])) {
                return -1;
            }
            fraction = body[index] - '0';
            index = index + 1;
            while (index < length && isDigit(body[index])) {
                index = index + 1;
            }
        }
        int tenths = whole * TENTHS_PER_UNIT + fraction;
        parsedTenths = negative ? -tenths : tenths;
        return index;
    }

    private static boolean isDigit(int value) {
        return value >= '0' && value <= '9';
    }

    private static boolean isKey(byte[] body, int start, int end, String key) {
        return end - start == key.length() && matchesAt(body, end, start, key);
    }

    private static boolean matchesAt(byte[] body, int length, int start, String expected) {
        int count = expected.length();
        if (start + count > length) {
            return false;
        }
        int index = 0;
        while (index < count) {
            if (body[start + index] != expected.charAt(index)) {
                return false;
            }
            index = index + 1;
        }
        return true;
    }
}
