package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Sealed interfaces and classes with explicitly permitted, final direct subclasses. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class SealedTypes {
    private SealedTypes() {
    }

    sealed interface Operation permits Add, Scale {
        int apply(int value);
    }

    static final class Add implements Operation {
        private final int amount;

        Add(int amount) {
            this.amount = amount;
        }

        @Override
        public int apply(int value) {
            return value + amount;
        }
    }

    sealed static class Scale implements Operation permits DoubleScale {
        private final int factor;

        Scale(int factor) {
            this.factor = factor;
        }

        @Override
        public int apply(int value) {
            return value * factor;
        }
    }

    static final class DoubleScale extends Scale {
        DoubleScale() {
            super(2);
        }
    }

    static int applyTwice(Operation operation, int value) {
        return operation.apply(operation.apply(value));
    }

    public static void main(String[] args) {
        Operation add = new Add(3);
        Operation scale = new DoubleScale();

        Serial.println(applyTwice(add, 4));
        Serial.println(applyTwice(scale, 5));
        Serial.println(scale.apply(add.apply(7)));
    }
}
