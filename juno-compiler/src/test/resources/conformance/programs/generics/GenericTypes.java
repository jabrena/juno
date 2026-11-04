package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Generic classes and methods after erasure, including the compiler-generated reference casts. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class GenericTypes {
    private GenericTypes() {
    }

    static final class Cell<T> {
        private T value;

        Cell(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        void set(T value) {
            this.value = value;
        }
    }

    static <T> T choose(boolean first, T left, T right) {
        return first ? left : right;
    }

    static <T> int sum(Cell<T> cell, int[] values) {
        int total = 0;
        for (int i = 0; i < values.length; i++) {
            total += values[i];
        }
        return total + (cell.get() == null ? 0 : 100);
    }

    public static void main(String[] args) {
        int[] first = {2, 3, 5};
        int[] second = {7, 11};
        int[] selected = choose(true, first, second);
        Cell<int[]> cell = new Cell<>(selected);

        Serial.println(sum(cell, cell.get()));
        cell.set(choose(false, first, second));
        Serial.println(sum(cell, cell.get()));
        Serial.println(choose(true, 13, 17));
    }
}
