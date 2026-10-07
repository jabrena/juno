package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/** Demonstrates fixed-size primitive arrays: indexing, loops, passing to methods, and a 2D array. */
public class Arrays {

    public static void main(String[] args) {
        Arrays arrays = new Arrays();
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Serial.println("Arrays demo");

        int[] numbers = new int[5];
        for (int index = 0; index < numbers.length; index++) {
            numbers[index] = (index + 1) * 10;
        }
        Serial.println("length: " + numbers.length);
        Serial.println("first: " + numbers[0]);
        Serial.println("last: " + numbers[numbers.length - 1]);
        Serial.println("sum: " + arrays.sum(numbers, numbers.length));
        Serial.println("max: " + arrays.max(numbers, numbers.length));

        arrays.reverse(numbers, numbers.length);
        Serial.println("after reverse, first: " + numbers[0]);

        double[] voltages = new double[4];
        voltages[0] = 3.5;
        voltages[1] = 5.0;
        voltages[2] = 1.5;
        voltages[3] = 2.0;
        double total = 0.0;
        for (int index = 0; index < voltages.length; index++) {
            total += voltages[index];
        }
        Serial.println("mean voltage: " + total / voltages.length);

        long[] big = new long[2];
        big[0] = 3000000000L;
        big[1] = big[0] * 2;
        Serial.println("big[1]: " + big[1]);

        int[][] grid = new int[2][3];
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 3; column++) {
                grid[row][column] = row * 3 + column;
            }
        }
        Serial.println("grid[1][2]: " + grid[1][2]);
    }

    // An array parameter has no known length, so callers pass the element count.
    private int sum(int[] values, int count) {
        int total = 0;
        for (int index = 0; index < count; index++) {
            total += values[index];
        }
        return total;
    }

    private int max(int[] values, int count) {
        int largest = values[0];
        for (int index = 1; index < count; index++) {
            if (values[index] > largest) {
                largest = values[index];
            }
        }
        return largest;
    }

    private void reverse(int[] values, int count) {
        for (int left = 0, right = count - 1; left < right; left++, right--) {
            int swap = values[left];
            values[left] = values[right];
            values[right] = swap;
        }
    }
}
