package io.github.jabrena.juno.collections;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;
import org.eclipse.collections.impl.list.primitive.IntInterval;

/** Demonstrates allocation-free Eclipse Collections primitive ranges. */
@Board(ArduinoNanoEsp32Board.class)
public final class EclipseCollectionsPrimitiveRanges {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("Eclipse Collections primitive ranges");

        IntInterval readings = IntInterval.oddsFromTo(7, 13);

        Serial.println("primitive readings: " + readings.size());
        Serial.println("contains 11: " + readings.contains(11));
        Serial.println("contains 9: " + readings.contains(9));
    }
}
