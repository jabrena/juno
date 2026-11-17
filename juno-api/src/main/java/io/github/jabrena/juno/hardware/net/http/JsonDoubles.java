package io.github.jabrena.juno.hardware.net.http;

/**
 * {@link JsonWriter#value(double)}'s number layout: up to {@value #DOUBLE_DIGITS} significant digits, rounded half up,
 * in plain notation from {@code 0.001} up to {@code 10^7} and as {@code d.dddE±n} otherwise, as
 * {@link Double#toString(double)} lays a number out. The digits go through the writer's scratch array, so nothing is
 * allocated.
 */
final class JsonDoubles {
    /** Significant digits a {@code double} is written with. */
    static final int DOUBLE_DIGITS = 15;

    private JsonDoubles() {
    }

    /** Writes the positive, finite {@code magnitude} through {@code writer}, using {@code digits} as scratch. */
    static void write(JsonWriter writer, double magnitude, byte[] digits) {
        int exponent = significantDigits(magnitude, digits);
        int count = DOUBLE_DIGITS;
        while (count > 1 && digits[count - 1] == 0) {
            count--;
        }
        if (exponent >= -3 && exponent < 7) {
            fixed(writer, digits, exponent, count);
            return;
        }
        writer.put(digit(digits, 0));
        writer.put('.');
        putDigits(writer, digits, 1, count);
        writer.put('E');
        if (exponent < 0) {
            writer.put('-');
        }
        int magnitudeOfExponent = exponent < 0 ? -exponent : exponent;
        if (magnitudeOfExponent >= 100) {
            writer.put((char) ('0' + magnitudeOfExponent / 100));
        }
        if (magnitudeOfExponent >= 10) {
            writer.put((char) ('0' + magnitudeOfExponent / 10 % 10));
        }
        writer.put((char) ('0' + magnitudeOfExponent % 10));
    }

    /**
     * Fills {@code digits} with the first {@value #DOUBLE_DIGITS} significant digits of the positive, finite
     * {@code magnitude}, rounded half up, and returns its decimal exponent.
     */
    private static int significantDigits(double magnitude, byte[] digits) {
        int exponent = 0;
        double value = magnitude;
        if (value < 1e-300) {
            value *= 1e16;
            exponent = -16;
        }
        double power = 1.0;
        double scaled;
        if (value >= 1.0) {
            while (value >= power * 10.0) {
                power *= 10.0;
                exponent++;
            }
            scaled = value / power;
        } else {
            while (value * power < 1.0) {
                power *= 10.0;
                exponent--;
            }
            scaled = value * power;
        }
        if (scaled >= 10.0) {
            scaled /= 10.0;
            exponent++;
        }
        for (int i = 0; i <= DOUBLE_DIGITS; i++) {
            int digit = (int) scaled;
            digit = digit > 9 ? 9 : digit;
            digits[i] = (byte) digit;
            scaled = (scaled - digit) * 10.0;
        }
        if (digits[DOUBLE_DIGITS] >= 5) {
            int i = DOUBLE_DIGITS - 1;
            while (i >= 0 && digits[i] == 9) {
                digits[i] = 0;
                i--;
            }
            if (i < 0) {
                digits[0] = 1;
                exponent++;
            } else {
                digits[i] = (byte) (digits[i] + 1);
            }
        }
        return exponent;
    }

    /** The {@code count} significant digits in plain notation, with at least one digit after the point. */
    private static void fixed(JsonWriter writer, byte[] digits, int exponent, int count) {
        if (exponent < 0) {
            writer.put('0');
            writer.put('.');
            for (int i = 1; i < -exponent; i++) {
                writer.put('0');
            }
            putDigits(writer, digits, 0, count);
            return;
        }
        for (int i = 0; i <= exponent; i++) {
            writer.put(i < count ? digit(digits, i) : '0');
        }
        writer.put('.');
        putDigits(writer, digits, exponent + 1, count);
    }

    /** Digits {@code [from, count)}, or a single {@code 0} when there are none. */
    private static void putDigits(JsonWriter writer, byte[] digits, int from, int count) {
        if (from >= count) {
            writer.put('0');
            return;
        }
        for (int i = from; i < count; i++) {
            writer.put(digit(digits, i));
        }
    }

    private static char digit(byte[] digits, int index) {
        return (char) ('0' + digits[index]);
    }
}
