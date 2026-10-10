package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * The patterns Juno's optimizer rewrites, checked against OpenJDK: constants folded into immediate operands (every
 * encoding class, and values Thumb-2 cannot encode), comparisons fused into branches, a power-of-two multiply as a
 * shift, and loops whose bounds checks the range analysis removes. Inputs come from an array so javac cannot fold
 * the expressions away.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class Optimizations {
    private static final int[] INPUTS = {0, 1, -1, 7, -7, 255, 256, 4095, 4096, -4096, 65535, 0x12345678,
            Integer.MAX_VALUE, Integer.MIN_VALUE};

    static int arithmetic(int x) {
        int result = x + 1;
        result ^= x + 255;
        result ^= x + 4095;
        result ^= x + 4096;
        result ^= x - 1;
        result ^= x - 4095;
        result ^= x + -300;
        result ^= x + 0x12345678;
        result ^= x - Integer.MIN_VALUE;
        result ^= x & 0xFF;
        result ^= x & 0xFFFFFF00;
        result ^= x & 0x00FF00FF;
        result ^= x | 0xFF00FF00;
        result ^= x | 0xFFFFF0FF;
        result ^= x ^ 0x80000000;
        result ^= x ^ 0x12345678;
        return result;
    }

    static int shifts(int x) {
        return (x << 1) ^ (x << 31) ^ (x >> 3) ^ (x >> 31) ^ (x >>> 1) ^ (x >>> 31) ^ (x << 32) ^ (x >> 33)
                ^ (x >>> -1) ^ (x * 2) ^ (x * 1024) ^ (x * 0x40000000) ^ (x * 1) ^ (x * 3) ^ (x * -4);
    }

    static int comparisons(int x) {
        int score = 0;
        if (x < 0) score += 1;
        if (x <= 7) score += 2;
        if (x > -7) score += 4;
        if (x >= 4096) score += 8;
        if (x == 255) score += 16;
        if (x != -4096) score += 32;
        if (x < 0x12345678) score += 64;
        if (x > Integer.MIN_VALUE) score += 128;
        boolean small = x < 256;
        boolean negative = x < -1;
        return score + (small ? 1000 : 0) + (negative ? 2000 : 0);
    }

    static int classify(int x) {
        switch (x) {
            case -4096:
                return 1;
            case 255:
                return 2;
            case 65535:
                return 3;
            case 0x12345678:
                return 4;
            default:
                return 5;
        }
    }

    static int loops(int seed) {
        int[] values = new int[16];
        for (int i = 0; i < values.length; i++) {
            values[i] = i * seed;
        }
        for (int i = 15; i >= 0; i--) {
            values[i] = values[i] + i;
        }
        values[seed & 15] = values[seed & 15] + 100;
        values[(seed & 0x7FFF) % 16] = values[(seed & 0x7FFF) % 16] - 7;
        int k = seed & 7;
        values[k] = values[k] + 1;
        int total = 0;
        for (int i = 0; i < 16; i++) {
            total = total * 31 + values[i];
        }
        return total;
    }

    public static void main(String[] args) {
        for (int i = 0; i < INPUTS.length; i++) {
            int x = INPUTS[i];
            Serial.println(arithmetic(x));
            Serial.println(shifts(x));
            Serial.println(comparisons(x));
            Serial.println(classify(x));
            Serial.println(loops(x));
        }
    }
}
