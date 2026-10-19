package io.github.jabrena.juno.language;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.util.Delay;
import io.github.jabrena.juno.language.Interfaces.Half;
import io.github.jabrena.juno.language.Interfaces.Scale;

/** Demonstrates closed-world interface dispatch with two reachable implementations. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class Interfaces {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        Gpio gpio = MCU.getGpio();
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
