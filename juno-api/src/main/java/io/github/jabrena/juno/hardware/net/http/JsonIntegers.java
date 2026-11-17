package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

/** {@link JsonValue#asLong()}'s rule, as JEP 540's: a validated number is an integer when its value is integral. */
final class JsonIntegers {
    private JsonIntegers() {
    }

    /**
     * Whether the validated number {@code [start, end)} has an integral value, as JEP 540's {@code asInt} accepts it:
     * {@code 123}, {@code 123.0}, {@code 2.3E2} and {@code 1500e-1} do; {@code 1.5} and {@code 1e-1} do not. Every digit
     * that the exponent leaves after the decimal point must be zero.
     */
    static boolean isIntegral(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        int digitsStart = JsonChars.at(buffer, text, start) == '-' ? start + 1 : start;
        int exponentStart = JsonNumbers.exponentStart(buffer, text, digitsStart, end);
        int integerDigits = integerDigits(buffer, text, digitsStart, exponentStart);
        int digits = digitCount(buffer, text, digitsStart, exponentStart);
        int point = integerDigits + (exponentStart < end ? JsonNumbers.exponent(buffer, text, exponentStart + 1, end) : 0);
        for (int k = point < 0 ? 0 : point; k < digits; k++) {
            if (digitAt(buffer, text, digitsStart, integerDigits, k) != '0') {
                return false;
            }
        }
        return true;
    }

    /**
     * The integral number {@code [start, end)} (see {@link #isIntegral}) as a {@code long}, or {@code 0} when it
     * overflows. Accumulates negatively, like {@link Long#parseLong(String)}, so {@link Long#MIN_VALUE} fits.
     */
    static long parseIntegral(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        boolean negative = JsonChars.at(buffer, text, start) == '-';
        int digitsStart = negative ? start + 1 : start;
        int exponentStart = JsonNumbers.exponentStart(buffer, text, digitsStart, end);
        int integerDigits = integerDigits(buffer, text, digitsStart, exponentStart);
        int digits = digitCount(buffer, text, digitsStart, exponentStart);
        int point = integerDigits + (exponentStart < end ? JsonNumbers.exponent(buffer, text, exponentStart + 1, end) : 0);
        long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        long minimumBeforeDigit = limit / 10;
        long result = 0L;
        for (int k = 0; k < point; k++) {
            int digit = k < digits ? digitAt(buffer, text, digitsStart, integerDigits, k) - '0' : 0;
            if (result == 0L && digit == 0) {
                continue;
            }
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

    /** The number of digits before the decimal point of the digits {@code [start, end)}. */
    private static int integerDigits(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        int p = start;
        while (p < end && JsonChars.at(buffer, text, p) != '.') {
            p++;
        }
        return p - start;
    }

    /** The number of digits in {@code [start, end)}, the decimal point not counted. */
    private static int digitCount(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        int integerDigits = integerDigits(buffer, text, start, end);
        return start + integerDigits < end ? end - start - 1 : integerDigits;
    }

    /** The {@code k}-th digit of the digits starting at {@code start}, skipping the decimal point. */
    private static int digitAt(byte @Nullable [] buffer, @Nullable String text, int start, int integerDigits, int k) {
        return JsonChars.at(buffer, text, k < integerDigits ? start + k : start + k + 1);
    }
}
