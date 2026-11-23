package io.github.jabrena.juno.collections;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;
import org.eclipse.collections.impl.list.primitive.IntInterval;

/** Demonstrates Eclipse Collections lists that store primitive ints and longs without boxing. */
@Board(ArduinoNanoEsp32Board.class)
public final class EclipseCollectionsPrimitiveLists {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("Eclipse Collections primitive lists");

        IntInterval temperatures = IntInterval.fromTo(18, 23);
        Serial.println("temperature count: " + temperatures.size());
        Serial.println("temperature sum: " + temperatures.sum());
        Serial.println("temperature max: " + temperatures.max());
        Serial.println("contains 21: " + temperatures.contains(21));

        int[] source = new int[4];
        source[0] = 7;
        source[1] = 11;
        source[2] = 13;
        source[3] = 17;
        int[] copy = new int[4];
        java.lang.System.arraycopy(source, 0, copy, 0, source.length);
        Serial.println("arraycopy last: " + copy[3]);

    }

}
