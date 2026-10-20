package io.github.jabrena.juno.api.hardware.net.http;

/** {@link Json}'s JSON numbers: validating one, and converting a validated one to {@code long} or {@code double}. */
final class JsonNumbers {
    private static final int MAX_SIGNIFICANT_DIGITS = 18;
    private static final int MAX_DECIMAL_EXPONENT = 400;

    private JsonNumbers() {
    }

    /** The index just past the JSON number starting at {@code start}, or {@link JsonSyntax#FAILED}. */
    static int parseNumber(byte[] buffer, int start, int end) {
        int p = start;
        if (p < end && buffer[p] == '-') {
            p++;
        }
        if (p >= end) {
            return JsonSyntax.FAILED;
        }
        if (buffer[p] == '0') {
            p++;
            if (p < end && JsonSyntax.isDigit(buffer[p])) {
                return JsonSyntax.FAILED;
            }
        } else {
            if (buffer[p] < '1' || buffer[p] > '9') {
                return JsonSyntax.FAILED;
            }
            p = skipDigits(buffer, p, end);
        }
        if (p < end && buffer[p] == '.') {
            p = requiredDigits(buffer, p + 1, end);
        }
        if (p != JsonSyntax.FAILED && p < end && (buffer[p] == 'e' || buffer[p] == 'E')) {
            p++;
            if (p < end && (buffer[p] == '+' || buffer[p] == '-')) {
                p++;
            }
            p = requiredDigits(buffer, p, end);
        }
        return p;
    }

    /** Whether {@code [start, end)} is an optional minus sign followed only by digits. */
    static boolean isInteger(byte[] buffer, int start, int end) {
        if (parseNumber(buffer, start, end) != end) {
            return false;
        }
        for (int p = buffer[start] == '-' ? start + 1 : start; p < end; p++) {
            if (!JsonSyntax.isDigit(buffer[p])) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code [start, end)} as a {@code long}, or {@code 0} when it overflows. Accumulates negatively, like
     * {@link Long#parseLong(String)}, so {@link Long#MIN_VALUE} parses without overflowing on the way.
     */
    static long parseLong(byte[] buffer, int start, int end) {
        boolean negative = buffer[start] == '-';
        long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        long minimumBeforeDigit = limit / 10;
        long result = 0L;
        for (int p = negative ? start + 1 : start; p < end; p++) {
            int digit = buffer[p] - '0';
            if (result < minimumBeforeDigit) {
                return 0L;
            }
            result *= 10;
            if (result < limit + digit) {
                return 0L;
            }
            result -= digit;
        }
        return negative ? result : -result;
    }

    /**
     * {@code [start, end)} as a {@code double}: up to 18 significant digits scaled by a power of ten, the same
     * operations in the same order on every board, or {@code 0.0} when it overflows.
     */
    static double parseDouble(byte[] buffer, int start, int end) {
        boolean negative = buffer[start] == '-';
        int exponentStart = exponentStart(buffer, start, end);
        double significand = 0.0;
        int significantDigits = 0;
        int decimalExponent = exponentStart < end ? exponent(buffer, exponentStart + 1, end) : 0;
        boolean fraction = false;
        for (int p = negative ? start + 1 : start; p < exponentStart; p++) {
            if (buffer[p] == '.') {
                fraction = true;
                continue;
            }
            int digit = buffer[p] - '0';
            if (significantDigits == 0 && digit == 0) {
                decimalExponent -= fraction ? 1 : 0;
            } else if (significantDigits < MAX_SIGNIFICANT_DIGITS) {
                significand = significand * 10.0 + digit;
                significantDigits++;
                decimalExponent -= fraction ? 1 : 0;
            } else {
                decimalExponent += fraction ? 0 : 1;
            }
        }
        return scaled(significand, decimalExponent, negative);
    }

    private static int exponentStart(byte[] buffer, int start, int end) {
        int p = start;
        while (p < end && buffer[p] != 'e' && buffer[p] != 'E') {
            p++;
        }
        return p;
    }

    /** The signed exponent whose optional sign starts at {@code start}, its magnitude capped near 1,000,000. */
    private static int exponent(byte[] buffer, int start, int end) {
        boolean negative = buffer[start] == '-';
        int exponent = 0;
        for (int p = buffer[start] == '+' || negative ? start + 1 : start; p < end; p++) {
            if (exponent < 100000) {
                exponent = exponent * 10 + (buffer[p] - '0');
            }
        }
        return negative ? -exponent : exponent;
    }

    private static double scaled(double significand, int decimalExponent, boolean negative) {
        if (significand == 0.0 || decimalExponent < -MAX_DECIMAL_EXPONENT) {
            return negative ? -0.0 : 0.0;
        }
        if (decimalExponent > MAX_DECIMAL_EXPONENT) {
            return 0.0;
        }
        double scale = 1.0;
        int scalePower = decimalExponent < 0 ? -decimalExponent : decimalExponent;
        for (int i = 0; i < scalePower; i++) {
            scale *= 10.0;
        }
        double value = decimalExponent < 0 ? significand / scale : significand * scale;
        if (value > Double.MAX_VALUE) {
            return 0.0; // overflowed to infinity
        }
        return negative ? -value : value;
    }

    /** Past one or more digits starting at {@code start}, or {@link JsonSyntax#FAILED} when there is none. */
    private static int requiredDigits(byte[] buffer, int start, int end) {
        return start < end && JsonSyntax.isDigit(buffer[start]) ? skipDigits(buffer, start, end) : JsonSyntax.FAILED;
    }

    private static int skipDigits(byte[] buffer, int start, int end) {
        int p = start;
        while (p < end && JsonSyntax.isDigit(buffer[p])) {
            p++;
        }
        return p;
    }
}
