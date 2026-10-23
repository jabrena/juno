package demo;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * Java's primitive types at their edges: every width, wrap-around, narrowing, sign extension, shifts, float and double
 * rounding, NaN, infinities, signed zero and subnormals, and the saturating floating-point to integer casts. An 8-bit
 * board must give the same answers as a JVM: its C int is 16 bits and its C double 32.
 */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class, ArduinoOneR3WiFiRev2Board.class})
public final class PrimitiveTypes {
    static byte b = (byte) 200;
    static short s = (short) 40000;
    static char c = (char) 65535;
    static long big = 0x7FFF_FFFF_FFFF_FFFFL;

    static int mix(int a, int b) {
        return (a * 31 + b) ^ (a >>> 7);
    }

    static float half(float value) {
        return value / 2.0f;
    }

    static double scale(double value, double factor) {
        return value * factor;
    }

    static int bits(boolean value) {
        return value ? 1 : 0;
    }

    public static void main(String[] args) {
        // byte, short, char: storage width, sign/zero extension and narrowing.
        Serial.println(b);
        Serial.println(s);
        Serial.println((int) c);
        Serial.println((byte) (b + 100));
        Serial.println((short) (s * 3));
        Serial.println((char) (c + 2));
        byte[] bytes = {-128, 127, (byte) 0x80};
        Serial.println(bytes[0] + bytes[1] + bytes[2]);
        short[] shorts = {Short.MIN_VALUE, Short.MAX_VALUE};
        Serial.println(shorts[0] - shorts[1]);
        char[] chars = {'A', 'z', 'é'};
        Serial.println(chars[0] + chars[1] + chars[2]);

        // int: wrap-around, MIN_VALUE corner cases, division and shifts.
        int max = Integer.MAX_VALUE;
        Serial.println(max + 1);
        Serial.println(Integer.MIN_VALUE / -1);
        Serial.println(Integer.MIN_VALUE % -1);
        Serial.println(-7 / 2);
        Serial.println(-7 % 2);
        Serial.println(7 % -3);
        Serial.println(123456789 * 987654321);
        Serial.println(-1 >>> 28);
        Serial.println(-1 >> 28);
        Serial.println(1 << 33);
        int shift = 35;
        Serial.println(5 << shift);
        Serial.println(-80 >> shift);
        Serial.println(-80 >>> shift);
        Serial.println(mix(0x12345678, -42));
        Serial.println(bits(-1 < 0) + bits(0x80000000 < 0));

        // long: 64-bit arithmetic, shifts and comparison.
        Serial.println(big + 1);
        Serial.println(Long.MIN_VALUE / -1);
        Serial.println(big * 3);
        Serial.println(-9_000_000_000_000_000_000L / 7);
        Serial.println(-9_000_000_000_000_000_000L % 7);
        Serial.println(1L << 63);
        Serial.println(-1L >>> 1);
        Serial.println(-1L >> 63);
        long shiftLong = 70;
        Serial.println(3L << shiftLong);
        Serial.println(bits(Long.MIN_VALUE < Long.MAX_VALUE) + bits(-1L > 1L));
        Serial.println((int) 0x1_0000_0001L);
        Serial.println((long) Integer.MIN_VALUE);

        // float: rounding, NaN and infinities, conversions.
        float third = 1.0f / 3.0f;
        Serial.println((int) (third * 1_000_000_000));
        Serial.println(bits(0.1f + 0.2f == 0.3f));
        Serial.println(bits(Float.NaN != Float.NaN));
        Serial.println(bits(1.0f / 0.0f > Float.MAX_VALUE));
        Serial.println((int) Float.POSITIVE_INFINITY);
        Serial.println((int) Float.NEGATIVE_INFINITY);
        Serial.println((long) 1e30f);
        Serial.println((int) half(7.0f));
        Serial.println((int) (Float.MIN_VALUE * 1e38f * 1e8f));
        Serial.println((long) (float) 0x7FFF_FFBF_FFFF_FFFFL);
        Serial.println((long) (float) 16_777_217);
        Serial.println((int) (5.5f % 2.0f * 10));
        Serial.println((int) (-5.5f % 2.0f * 10));

        // double: binary64 everywhere, even where the C double is 32 bits.
        double tenth = 0.1;
        double sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += tenth;
        }
        Serial.println(bits(sum == 1.0));
        Serial.println(bits(sum < 1.0));
        Serial.println((long) (sum * 1e17));
        Serial.println((long) scale(123456789.0, 1e9));
        Serial.println((long) Math.sqrt(2.0e18));
        Serial.println((long) (1.0 / 3.0 * 1e16));
        Serial.println(bits(Double.MIN_VALUE > 0));
        Serial.println(bits(Double.MIN_VALUE / 2 == 0));
        Serial.println((long) (Double.MIN_VALUE * 1e308 * 1e16 * 1e16));
        Serial.println((long) Double.NaN);
        Serial.println((long) 1e300);
        Serial.println((int) -1e300);
        Serial.println(bits(-0.0 == 0.0));
        Serial.println(bits(1 / -0.0 < 0));
        Serial.println((int) (double) Float.MAX_VALUE == Integer.MAX_VALUE ? 1 : 0);
        Serial.println((int) ((float) 1e40 > 0 ? 1 : 0));
        Serial.println((long) (7.25 % 2.5 * 100));
        Serial.println((long) Math.floor(-2.5) + (long) Math.ceil(-2.5) * 10);
        Serial.println(Math.round(2.5) + Math.round(-2.5) * 10 + Math.round(-0.49999999999999994) * 100);
        Serial.println(Math.round(2.5f) + Math.round(-2.5f) * 10);
        Serial.println((long) (Math.max(-0.0, 0.0) * 0 == 0 ? 1 : 0));
        Serial.println((long) Math.abs(-3.75e10));
        Serial.println(Long.MAX_VALUE == (long) (double) Long.MAX_VALUE ? 1 : 0);
        Serial.println((long) (double) (Long.MAX_VALUE - 512));
        Serial.println("float " + 1.5f + " double " + 2.25 + " neg " + (-0.125));
    }
}
