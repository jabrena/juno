package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Unlabeled and labeled break and continue through nested loops and switches, and return from inside loops. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoNanoEsp32Board.class, M5StackCardputerBoard.class, AiThinkerEsp32CamBoard.class})
public final class JumpsAndLabels {
    private JumpsAndLabels() {
    }

    static int firstMatch(int target) {
        for (int i = 0;; i++) {
            switch (i) {
                case 3:
                    return i * 10;
                default:
                    if (i == target) {
                        return -i;
                    }
            }
        }
    }

    public static void main(String[] args) {
        outer:
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (c == 2) {
                    continue outer;
                }
                if (r == 2) {
                    break outer;
                }
                Serial.println(r * 10 + c);
            }
        }

        for (int s = 0; s < 4; s++) {
            switch (s) {
                case 1:
                    continue;
                default:
                    break;
            }
            Serial.println(s);
        }

        int c = 0;
        do {
            c++;
            if (c < 3) {
                continue;
            }
        } while (c < 5);
        Serial.println(c);

        Serial.println(firstMatch(5));
        Serial.println(firstMatch(1));
    }
}
