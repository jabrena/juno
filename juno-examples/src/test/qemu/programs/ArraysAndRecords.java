package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;

/** arrays, records, strings and enums. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class ArraysAndRecords {
    record Point(int x, int y) {
        int dot(Point other) {
            return x * other.x + y * other.y;
        }
    }

    enum Level {
        LOW, MEDIUM, HIGH
    }

    static int sum(int[] values, int count) {
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += values[i];
        }
        return total;
    }

    static void bubbleSort(int[] values, int count) {
        for (int i = 0; i < count; i++) {
            for (int j = 0; j + 1 < count - i; j++) {
                if (values[j] > values[j + 1]) {
                    int swap = values[j];
                    values[j] = values[j + 1];
                    values[j + 1] = swap;
                }
            }
        }
    }

    static int weight(Level level) {
        return switch (level) {
            case LOW -> 1;
            case MEDIUM -> 10;
            case HIGH -> 100;
        };
    }

    public static void main(String[] args) {
        int[] data = new int[8];
        for (int i = 0; i < data.length; i++) {
            data[i] = (i * 37 + 11) % 19;
        }
        Serial.println(sum(data, data.length));
        bubbleSort(data, data.length);
        for (int i = 0; i < data.length; i++) {
            Serial.print(data[i]);
            Serial.print(" ");
        }
        Serial.println("");
        byte[] bytes = new byte[4];
        bytes[0] = (byte) 200;
        bytes[1] = (byte) -3;
        Serial.println(bytes[0] + bytes[1]);
        Serial.println(bytes[0] & 0xff);
        short[] shorts = new short[2];
        shorts[0] = (short) 70000;
        Serial.println(shorts[0]);
        char letter = 'A';
        letter += 2;
        Serial.println(letter);
        Point a = new Point(3, 4);
        Point b = new Point(-2, 5);
        Serial.println(a.dot(b));
        Serial.println(a.x() + a.y() * 10);
        Serial.println(weight(Level.HIGH) + weight(Level.LOW));
        Serial.println("done");
    }
}
