package io.github.jabrena.juno.api.thirdparty.ir;

import io.github.jabrena.juno.api.hardware.MCU;

import io.github.jabrena.juno.api.hardware.io.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.io.serial.Serial;
import io.github.jabrena.juno.api.time.Clock;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Checks an IR receiver module on its own: once a second it prints how many infrared bursts the receiver
 * reported, as {@code bursts: N}. Wire the receiver's OUT to pin 2, with VCC and GND; nothing is needed on
 * the transmit side. With nothing pointed at it the count stays at or near zero; press any button of a TV
 * remote towards it and the count jumps into the tens or hundreds. A count that never moves means the module
 * gets no power or its output is not on pin 2; one that is high even in the dark means noise, e.g. a missing
 * ground. Pick the board with {@code -Djuno.board}.
 */
public class InfraredReceiverProbe {
    private static final Clock CLOCK = MCU.getClock();
    private static final int RECEIVE_PIN = 2;
    private static final int NO_PIN = -1;
    private static final int BAUD = 2400;
    private static final int WINDOW_MILLIS = 1000;
    private static final int START_MILLIS = 2000;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        InfraredReceiverProbe app = new InfraredReceiverProbe();
        Infrared.begin(RECEIVE_PIN, NO_PIN, BAUD);
        Delay.millis(START_MILLIS);
        Serial.println("IR receiver probe: point a TV remote at it");

        while (true) {
            Serial.print("bursts: ");
            Serial.println(app.countBursts());
        }
    }

    /** How many times the receiver went from idle to seeing a burst during one window. */
    private int countBursts() {
        int bursts = 0;
        boolean previous = false;
        int started = CLOCK.millis();
        while (CLOCK.millis() - started < WINDOW_MILLIS) {
            boolean current = Infrared.receiving();
            if (current && !previous) {
                bursts++;
            }
            previous = current;
        }
        return bursts;
    }

}
