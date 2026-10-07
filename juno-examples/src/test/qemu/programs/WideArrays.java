package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;

/** long[], double[] and float[] arrays: element access, passing to methods, and arena churn. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class WideArrays {
    static long sum(long[] values, int count) {
        long total = 0;
        for (int i = 0; i < count; i++) {
            total += values[i];
        }
        return total;
    }

    static void scale(double[] values, int count, double factor) {
        for (int i = 0; i < count; i++) {
            values[i] = values[i] * factor;
        }
    }

    public static void main(String[] args) {
        long[] wide = new long[4];
        for (int i = 0; i < wide.length; i++) {
            wide[i] = (1L << (30 + i * 8)) + i;
        }
        Serial.println(wide[3]);
        Serial.println(sum(wide, wide.length));
        wide[1] = -wide[1];
        Serial.println(sum(wide, 3));

        double[] reals = new double[3];
        reals[0] = 1.5e9;
        reals[1] = -2.25e9;
        reals[2] = reals[0] + reals[1];
        scale(reals, 3, 4.0);
        Serial.println((long) reals[0]);
        Serial.println((long) reals[1]);
        Serial.println((long) reals[2]);

        float[] floats = new float[5];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = i * 1.5f;
        }
        float total = 0;
        for (int i = 0; i < floats.length; i++) {
            total += floats[i];
        }
        Serial.println((int) (total * 10));

        long checksum = 0;
        for (int round = 0; round < 200; round++) {
            long[] scratch = new long[16];
            scratch[round % 16] = round * 3_000_000_000L;
            checksum += sum(scratch, 16);
        }
        Serial.println(checksum);
    }
}
