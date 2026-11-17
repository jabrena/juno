package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

/** {@link Json}'s JSON numbers: validating one, and converting a validated one to {@code long} or {@code double}. */
final class JsonNumbers {
    private static final int MAX_SIGNIFICANT_DIGITS = 18;
    private static final int MAX_DECIMAL_EXPONENT = 400;

    private JsonNumbers() {
    }

    /** The index just past the JSON number starting at {@code start}, or {@link JsonSyntax#FAILED}. */
    static int parseNumber(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        int p = start;
        if (p < end && JsonChars.at(buffer, text, p) == '-') {
            p++;
        }
        if (p >= end) {
            return JsonSyntax.FAILED;
        }
        if (JsonChars.at(buffer, text, p) == '0') {
            p++;
            if (p < end && JsonSyntax.isDigit(JsonChars.at(buffer, text, p))) {
                return JsonSyntax.FAILED;
            }
        } else {
            if (JsonChars.at(buffer, text, p) < '1' || JsonChars.at(buffer, text, p) > '9') {
                return JsonSyntax.FAILED;
            }
            p = skipDigits(buffer, text, p, end);
        }
        if (p < end && JsonChars.at(buffer, text, p) == '.') {
            p = requiredDigits(buffer, text, p + 1, end);
        }
        if (p != JsonSyntax.FAILED && p < end && (JsonChars.at(buffer, text, p) == 'e' || JsonChars.at(buffer, text, p) == 'E')) {
            p++;
            if (p < end && (JsonChars.at(buffer, text, p) == '+' || JsonChars.at(buffer, text, p) == '-')) {
                p++;
            }
            p = requiredDigits(buffer, text, p, end);
        }
        return p;
    }

    /** Whether {@code [start, end)} is an optional minus sign followed only by digits. */
    static boolean isInteger(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        if (parseNumber(buffer, text, start, end) != end) {
            return false;
        }
        for (int p = JsonChars.at(buffer, text, start) == '-' ? start + 1 : start; p < end; p++) {
            if (!JsonSyntax.isDigit(JsonChars.at(buffer, text, p))) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code [start, end)} as a {@code long}, or {@code 0} when it overflows. Accumulates negatively, like
     * {@link Long#parseLong(String)}, so {@link Long#MIN_VALUE} parses without overflowing on the way.
     */
    static long parseLong(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        boolean negative = JsonChars.at(buffer, text, start) == '-';
        long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        long minimumBeforeDigit = limit / 10;
        long result = 0L;
        for (int p = negative ? start + 1 : start; p < end; p++) {
            int digit = JsonChars.at(buffer, text, p) - '0';
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
    static double parseDouble(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        boolean negative = JsonChars.at(buffer, text, start) == '-';
        int exponentStart = exponentStart(buffer, text, start, end);
        double significand = 0.0;
        int significantDigits = 0;
        int decimalExponent = exponentStart < end ? exponent(buffer, text, exponentStart + 1, end) : 0;
        boolean fraction = false;
        for (int p = negative ? start + 1 : start; p < exponentStart; p++) {
            if (JsonChars.at(buffer, text, p) == '.') {
                fraction = true;
                continue;
            }
            int digit = JsonChars.at(buffer, text, p) - '0';
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

    private static int exponentStart(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        int p = start;
        while (p < end && JsonChars.at(buffer, text, p) != 'e' && JsonChars.at(buffer, text, p) != 'E') {
            p++;
        }
        return p;
    }

    /** The signed exponent whose optional sign starts at {@code start}, its magnitude capped near 1,000,000. */
    private static int exponent(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        boolean negative = JsonChars.at(buffer, text, start) == '-';
        int exponent = 0;
        for (int p = JsonChars.at(buffer, text, start) == '+' || negative ? start + 1 : start; p < end; p++) {
            if (exponent < 100000) {
                exponent = exponent * 10 + (JsonChars.at(buffer, text, p) - '0');
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
    private static int requiredDigits(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        return start < end && JsonSyntax.isDigit(JsonChars.at(buffer, text, start)) ? skipDigits(buffer, text, start, end) : JsonSyntax.FAILED;
    }

    private static int skipDigits(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        int p = start;
        while (p < end && JsonSyntax.isDigit(JsonChars.at(buffer, text, p))) {
            p++;
        }
        return p;
    }
}
