package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Constructor chaining, inherited fields, overriding, super calls, and interface dispatch. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class InheritanceDispatch {
    private InheritanceDispatch() {
    }

    interface Score {
        int score(int value);
    }

    static class Offset {
        final int offset;

        Offset(int offset) {
            this.offset = offset;
        }

        int adjust(int value) {
            return value + offset;
        }
    }

    static final class DoubledOffset extends Offset implements Score {
        DoubledOffset(int offset) {
            super(offset);
        }

        @Override
        int adjust(int value) {
            return super.adjust(value) * 2;
        }

        @Override
        public int score(int value) {
            return adjust(value) + offset;
        }
    }

    static final class NegatedOffset extends Offset implements Score {
        NegatedOffset(int offset) {
            super(offset);
        }

        @Override
        public int score(int value) {
            return -adjust(value);
        }
    }

    static int total(Score[] scores, int value) {
        int total = 0;
        for (int i = 0; i < scores.length; i++) {
            total += scores[i].score(value + i);
        }
        return total;
    }

    public static void main(String[] args) {
        Offset base = new Offset(3);
        Offset doubled = new DoubledOffset(4);
        Score[] scores = {new DoubledOffset(2), new NegatedOffset(5)};

        Serial.println(base.adjust(7));
        Serial.println(doubled.adjust(7));
        Serial.println(total(scores, 10));
    }
}
