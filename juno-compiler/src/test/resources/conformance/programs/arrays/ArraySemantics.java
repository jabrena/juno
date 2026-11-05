package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Array initializers, creation, multidimensional access, lengths, stores, and bounds exceptions. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class ArraySemantics {
    private ArraySemantics() {
    }

    static int boundedRead(int[] values, int index) {
        try {
            if (index < 0 || index >= values.length) {
                throw new ArrayIndexOutOfBoundsException();
            }
            return values[index];
        } catch (ArrayIndexOutOfBoundsException exception) {
            return -100;
        }
    }

    public static void main(String[] args) {
        int[] values = {3, 1, 4, 1};
        values[1] = values[0] + values[2];
        int[][] grid = new int[2][3];
        grid[1][2] = values[1];
        byte[] bytes = new byte[3];
        bytes[2] = (byte) 250;

        Serial.println(values.length);
        Serial.println(values[1]);
        Serial.println(grid.length * 10 + grid[1].length);
        Serial.println(grid[1][2]);
        Serial.println(bytes[2]);
        Serial.println(boundedRead(values, -1));
        Serial.println(boundedRead(values, values.length));
    }
}
