package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Exercises javac-generated record toString, hashCode and equals methods. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class RecordObjectMethods {
    record Point(int x, String label) {
    }

    public static void main(String[] args) {
        Point first = new Point(3, "east");
        Point same = new Point(3, "east");
        Point different = new Point(4, "east");
        Serial.println(first.toString());
        Serial.println(first.hashCode());
        Serial.println(first.equals(same));
        Serial.println(first.equals(different));
        Serial.println(first.equals(null));
        Serial.println(first.equals("east"));
    }
}
