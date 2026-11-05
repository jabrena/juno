package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Record components, explicitly declared members, and normal and compact canonical constructors. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class RecordClasses {
    private RecordClasses() {
    }

    record Point(int x, int y) {
        Point(int x, int y) {
            this.x = x + 1;
            this.y = y - 1;
        }

        int dot(Point other) {
            return x * other.x + y * other.y;
        }
    }

    record Dimensions(int width, int height) {
        Dimensions {
            width += 2;
            height *= 3;
        }

        int area() {
            return width * height;
        }
    }

    public static void main(String[] args) {
        Point first = new Point(3, 4);
        Point second = new Point(-2, 5);
        Dimensions dimensions = new Dimensions(5, 4);

        Serial.println(first.x());
        Serial.println(first.y());
        Serial.println(first.dot(second));
        Serial.println(dimensions.width());
        Serial.println(dimensions.height());
        Serial.println(dimensions.area());
    }
}
