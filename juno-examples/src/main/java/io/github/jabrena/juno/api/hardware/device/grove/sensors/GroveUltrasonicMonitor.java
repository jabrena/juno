package io.github.jabrena.juno.api.hardware.device.grove.sensors;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveUltrasonicRanger;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Prints the distance the Grove Ultrasonic Ranger on the Grove Base Shield's D5 socket measures, in millimetres, five
 * times a second, or "no echo" when nothing answers within 30 ms (beyond about 5 m, or a soft or slanted surface). The
 * ranger times its echo with {@code Gpio.pulseIn}, in the core. Pick the board with {@code -Djuno.board}.
 */
@Board({ArduinoUnoR3Board.class, ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class GroveUltrasonicMonitor {
    private static final int PERIOD_MILLIS = 200;
    private static final float MILLIMETRES_PER_METRE = 1000f;

    private GroveUltrasonicMonitor() {
    }

    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(MCU.getGpio());
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(MCU.getGpio(), MCU.getClock(),
                shield.getD5());
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);
        while (true) {
            float metres = ranger.getRange();
            // NaN, no echo, is the one value not equal to itself.
            if (metres != metres) {
                Serial.println("no echo");
            } else {
                Serial.print("distance mm=");
                Serial.println((int) (metres * MILLIMETRES_PER_METRE));
            }
            Delay.millis(PERIOD_MILLIS);
        }
    }
}
