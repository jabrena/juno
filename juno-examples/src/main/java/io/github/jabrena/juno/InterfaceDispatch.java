package io.github.jabrena.juno;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.Gpio;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Demonstrates closed-world interface dispatch with two reachable implementations. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class InterfaceDispatch {

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

    static Scale choose(boolean highInput) {
        return highInput ? new Half() : new Double();
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Scale scale = choose(Gpio.analogRead(0) > 512);
        int result = scale.apply(100);
        while (true) {
            Serial.print("Interface dispatch result: ");
            Serial.println(result);
            Delay.millis(1000);
        }
    }
}
