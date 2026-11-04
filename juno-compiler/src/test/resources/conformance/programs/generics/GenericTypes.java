package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * Generic classes and methods after erasure, including the compiler-generated reference casts. Type variables are
 * bounded by a program class so their erasure stays inside the closed world of the program.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class GenericTypes {
    private GenericTypes() {
    }

    static class Token {
        private final int weight;

        Token(int weight) {
            this.weight = weight;
        }

        int weight() {
            return weight;
        }
    }

    static final class Cell<T extends Token> {
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

    static <T extends Token> T choose(boolean first, T left, T right) {
        return first ? left : right;
    }

    static <T extends Token> int sum(Cell<T> cell, int count) {
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += cell.get().weight();
        }
        return total;
    }

    public static void main(String[] args) {
        Token light = new Token(2);
        Token heavy = new Token(7);
        Cell<Token> cell = new Cell<>(light);

        Serial.println(sum(cell, 3));
        cell.set(choose(false, light, heavy));
        Serial.println(sum(cell, 3));
        Serial.println(choose(true, light, heavy).weight());
    }
}
