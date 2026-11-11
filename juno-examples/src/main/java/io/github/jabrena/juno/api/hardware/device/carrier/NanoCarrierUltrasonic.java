package io.github.jabrena.juno.api.hardware.device.carrier;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveUltrasonicRanger;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/** Reads the Grove ultrasonic ranger connected to the carrier's A2/A3 socket. */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoCarrierUltrasonic {
    private NanoCarrierUltrasonic() {
    }

    public static void main(String[] args) {
        Gpio gpio = ArduinoNano.getGpio();
        Clock clock = ArduinoNano.getClock();
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(gpio);
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(gpio, clock, carrier.getGroveA2());
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        while (true) {
            float metres = ranger.getRange();
            if (metres != metres) {
                Serial.println("no echo");
            } else {
                Serial.print("distance cm=");
                Serial.println(metres * 100.0f);
            }
            Delay.millis(500);
        }
    }
}
