package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * static final lookup tables of every element type read from flash, a table indexed by another table, and one
 * passed to a method, which therefore stays in the arena.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class ConstantTables {
    static final boolean[] FLAGS = {true, false, true};
    static final byte[] BYTES = {-128, -1, 0, 127};
    static final char[] CHARS = {'J', 'u', 'n', 'o', '￿'};
    static final short[] SHORTS = {-32768, -300, 0, 300, 32767};
    static final int[] INTS = {Integer.MIN_VALUE, -7, 0, 7, Integer.MAX_VALUE};
    static final long[] LONGS = {Long.MIN_VALUE, -(1L << 40) - 3, 0L, 1L << 40, Long.MAX_VALUE};
    static final float[] FLOATS = {-1.5f, 0.0f, 0.25f, 3.0e9f};
    static final double[] DOUBLES = {-2.5, 0.0, 0.125, 1.0e15};
    static final short[] ORDER = {4, 0, 3, 1, 2};
    static final int[] PASSED = {10, 20, 30};

    static int sum(int[] values, int count) {
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += values[i];
        }
        return total;
    }

    public static void main(String[] args) {
        int flags = 0;
        for (int i = 0; i < FLAGS.length; i++) {
            flags = flags * 2 + (FLAGS[i] ? 1 : 0);
        }
        Serial.println(flags);
        int bytes = 0;
        for (int i = 0; i < BYTES.length; i++) {
            bytes = bytes * 3 + BYTES[i];
        }
        Serial.println(bytes);
        int chars = 0;
        for (int i = 0; i < CHARS.length; i++) {
            chars = chars * 31 + CHARS[i];
        }
        Serial.println(chars);
        for (int i = 0; i < ORDER.length; i++) {
            Serial.println(SHORTS[ORDER[i]]);
        }
        Serial.println(INTS[0] + INTS[4]);
        Serial.println(INTS[1] * INTS[3]);
        Serial.println(LONGS[0]);
        Serial.println(LONGS[1] + LONGS[3]);
        Serial.println(LONGS[4]);
        Serial.println((int) (FLOATS[0] * 100) + (int) (FLOATS[2] * 100));
        Serial.println((long) FLOATS[3]);
        Serial.println((long) (DOUBLES[0] * 1000) + (long) (DOUBLES[2] * 1000));
        Serial.println((long) DOUBLES[3]);
        Serial.println(sum(PASSED, 3));
        Serial.println(LONGS.length + DOUBLES.length);
    }
}
