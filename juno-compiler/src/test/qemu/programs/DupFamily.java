package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * The expressions javac compiles to the dup family beyond plain dup: a field post-increment used as a value
 * (dup_x1, dup2_x1 for long and double), compound assignment to an array element (dup2), an array element
 * post-increment used as a value (dup_x2, dup2_x2 for long and double), chained assignment through a field (dup_x1)
 * and long post-increments on locals and statics (dup2). Floating-point results print scaled to ints, as the other
 * programs do, since the board formats floats differently.
 */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class DupFamily {
    private static long ticks = 40;
    private int next = 3;
    private long total = 5_000_000_000L;
    private double level = 1.5;
    private int mirror;

    public static void main(String[] args) {
        DupFamily counters = new DupFamily();
        int[] ints = {10, 20, 30};
        long[] longs = {7_000_000_000L, -1L};
        double[] doubles = {0.25, -2.5};
        float[] floats = {1.5f, 3f};

        int index = ints[counters.next++ - 3];
        Serial.println(index);
        Serial.println(counters.next);
        Serial.println(counters.total++);
        Serial.println(counters.total);
        Serial.println((int) (counters.level++ * 100));
        Serial.println((int) (counters.level * 100));

        ints[1] += 5;
        ints[2] *= 3;
        longs[0] += 1;
        doubles[1] *= 4;
        floats[0] -= 0.5f;
        Serial.println(ints[1]);
        Serial.println(ints[2]);
        Serial.println(longs[0]);
        Serial.println((int) (doubles[1] * 100));
        Serial.println((int) (floats[0] * 100));

        Serial.println(ints[0]++);
        Serial.println(ints[0]);
        Serial.println(longs[1]--);
        Serial.println(longs[1]);
        Serial.println((int) (doubles[0]++ * 100));
        Serial.println((int) (doubles[0] * 100));
        Serial.println((int) (floats[1]++ * 100));
        Serial.println((int) (floats[1] * 100));

        int copy = counters.mirror = 42;
        Serial.println(copy + counters.mirror);

        long local = 9_999_999_999L;
        long before = local++;
        Serial.println(before);
        Serial.println(local);
        Serial.println(ticks++);
        Serial.println(ticks);
        for (int i = 0; i < 3; i++) {
            ints[i % 3] += ints[(i + 1) % 3]++;
        }
        Serial.println(ints[0] + ints[1] * 1000 + ints[2] * 1_000_000);
    }
}
