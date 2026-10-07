package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;

/** interface dispatch, lambdas, method references and captured values. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class Dispatch {
    interface Shape {
        int area();
    }

    record Square(int side) implements Shape {
        public int area() {
            return side * side;
        }
    }

    record Rectangle(int width, int height) implements Shape {
        public int area() {
            return width * height;
        }
    }

    interface Combine {
        int apply(int a, int b);
    }

    interface Transform {
        int apply(int value);
    }

    static int total(Shape[] shapes, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += shapes[i].area();
        }
        return sum;
    }

    static int apply(Combine operator, int a, int b) {
        return operator.apply(a, b);
    }

    static int twice(Transform operator, int value) {
        return operator.apply(operator.apply(value));
    }

    static int add(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) {
        Shape[] shapes = {new Square(3), new Rectangle(4, 5), new Square(2)};
        Serial.println(total(shapes, 3));
        Serial.println(shapes[1].area() * 2);
        Serial.println(apply((a, b) -> a * b, 6, 7));
        Serial.println(apply(Dispatch::add, 20, 22));
        int offset = 100;
        Serial.println(twice(value -> value + offset, 1));
        Serial.println(twice(value -> value * 3, 5));
    }
}
