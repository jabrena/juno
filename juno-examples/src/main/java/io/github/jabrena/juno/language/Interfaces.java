package io.github.jabrena.juno.language;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.language.Interfaces.Half;
import io.github.jabrena.juno.language.Interfaces.Scale;
import io.github.jabrena.juno.util.Delay;

/** Demonstrates closed-world interface dispatch with two reachable implementations. */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class})
@SuppressWarnings("java:S2189")
public final class Interfaces {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        Gpio gpio = ArduinoOne.getGpio();
        Interfaces interfaces = new Interfaces();
        Scale scale = interfaces.choose(gpio.analogRead(0) > 512);
        int result = scale.apply(100);
        while (true) {
            Serial.print("Interface dispatch result: ");
            Serial.println(result);
            Delay.millis(1000);
        }
    }

    interface Scale {
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
    }

    private Scale choose(boolean highInput) {
        return highInput ? new Half() : new Double();
    }
}
