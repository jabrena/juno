package io.github.jabrena.juno.language;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Demonstrates abstract classes, inherited methods, {@code super}, overriding, and virtual dispatch. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoNanoEsp32Board.class, ArduinoUnoR3Board.class,
        ArduinoUnoR3WiFiRev2Board.class, M5StackCardputerBoard.class})
public final class Inheritance {

    private Inheritance() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Shape circle = new Circle(3);
        Shape square = new Square(4);

        Serial.println("Inheritance demo");
        Serial.println("circle score: " + circle.score());
        Serial.println("square score: " + square.score());
        Serial.println("total: " + (circle.score() + square.score()));

        while (true) {
            Delay.millis(1000);
        }
    }

    abstract static class Shape {
        private final int size;

        Shape(int size) {
            this.size = size;
        }

        int size() {
            return size;
        }

        int score() {
            return size;
        }
    }

    static final class Circle extends Shape {
        Circle(int size) {
            super(size);
        }

        @Override
        int score() {
            return super.score() * 2;
        }
    }

    static final class Square extends Shape {
        Square(int size) {
            super(size);
        }

        @Override
        int score() {
            return size() * size();
        }
    }
}
