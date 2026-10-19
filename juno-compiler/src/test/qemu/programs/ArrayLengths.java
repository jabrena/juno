package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.io.serial.Serial;

/**
 * {@code array.length} on arrays whose size is not known where it is read: a parameter, a field, a returned array,
 * the rows of a two-dimensional array and an enum's {@code values()}, plus arrays that keep their length
 * across collections.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class, ArduinoUnoR3.class})
public final class ArrayLengths {
    enum Color { RED, GREEN, BLUE }

    private final byte[] field = new byte[7];

    int sum(int[] values) {
        int total = 0;
        for (int i = 0; i < values.length; i++) {
            total += values[i];
        }
        return total;
    }

    int lengthOf(byte[] bytes) {
        return bytes.length;
    }

    int lengthOf(char[] chars) {
        return chars.length;
    }

    int lengthOf(short[] shorts) {
        return shorts.length;
    }

    int fieldLength() {
        return field.length;
    }

    int[] makeFive() {
        int[] result = new int[5];
        result[4] = 9;
        return result;
    }

    int churn(int rounds) {
        int checksum = 0;
        for (int round = 0; round < rounds; round++) {
            byte[] scratch = new byte[40];
            checksum += lengthOf(scratch);
        }
        return checksum;
    }

    public static void main(String[] args) {
        ArrayLengths lengths = new ArrayLengths();
        int[] three = {4, 5, 6};
        Serial.println(lengths.sum(three));
        Serial.println(lengths.fieldLength());
        Serial.println(lengths.lengthOf(new char[11]));
        Serial.println(lengths.lengthOf(new short[2]));
        Serial.println(lengths.makeFive().length);
        Serial.println(lengths.sum(lengths.makeFive()));
        int[][] grid = new int[3][4];
        Serial.println(grid.length);
        Serial.println(grid[2].length);
        Serial.println(Color.values().length);
        byte[] kept = new byte[13];
        Serial.println(lengths.churn(300));
        Serial.println(lengths.lengthOf(kept));
    }
}
