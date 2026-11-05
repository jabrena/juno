package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** while with zero iterations, do that runs once, basic for with comma-separated headers, and enhanced for over arrays. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class Loops {
    private Loops() {
    }

    public static void main(String[] args) {
        int zero = 0;
        while (zero > 0) {
            Serial.println(-1);
            zero--;
        }
        Serial.println(zero);

        int once = 5;
        do {
            once += 10;
        } while (once < 0);
        Serial.println(once);

        for (int x = 0, y = 6; x < y; x += 2, y--) {
            Serial.println(x * 100 + y);
        }

        int[] empty = new int[0];
        for (int e : empty) {
            Serial.println(-1);
        }

        int[] data = {3, 1, 4, 1, 5};
        int sum = 0;
        for (int d : data) {
            sum += d;
        }
        Serial.println(sum);

        int ticks = 0;
        for (;;) {
            ticks++;
            if (ticks == 3) {
                break;
            }
        }
        Serial.println(ticks);
    }
}
