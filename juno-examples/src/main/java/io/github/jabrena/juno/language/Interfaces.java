package io.github.jabrena.juno.language;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.language.Interfaces.Half;
import io.github.jabrena.juno.language.Interfaces.Scale;
import io.github.jabrena.juno.util.Delay;

/** Demonstrates interface dispatch, inherited default methods, and an overriding implementation. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoNanoEsp32Board.class, ArduinoUnoR3Board.class,
        ArduinoUnoR3WiFiRev2Board.class, M5StackCardputerBoard.class})
public final class Interfaces {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        Gpio gpio = ArduinoUno.getGpio();
        Interfaces interfaces = new Interfaces();
        Scale scale = interfaces.choose(gpio.analogRead(0) > 512);
        int result = scale.apply(100);
        int factor = scale.factor();
        while (true) {
            Serial.print("Interface dispatch result: ");
            Serial.println(result * factor);
            Delay.millis(1000);
        }
    }

    interface Described {
        default int factor() {
            return 1;
        }
    }

    interface Scale extends Described {
        int apply(int value);
    }

    static final class Half implements Scale {
        @Override
        public int apply(int value) {
            return value / 2;
        }
    }

    static final class Double implements Scale {
        @Override
        public int apply(int value) {
            return value * 2;
        }

        @Override
        public int factor() {
            return 2;
        }
    }

    private Scale choose(boolean highInput) {
        return highInput ? new Half() : new Double();
    }
}
