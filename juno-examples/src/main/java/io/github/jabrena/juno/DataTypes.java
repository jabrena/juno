package io.github.jabrena.juno;

import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Prints the size and range of every primitive numeric type; javac folds each line into one string constant. */
public class DataTypes {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Serial.println("Data types demo");
        Serial.println("byte: " + Byte.BYTES + " bytes, range: " + Byte.MIN_VALUE + " to " + Byte.MAX_VALUE);
        Serial.println("short: " + Short.BYTES + " bytes, range: " + Short.MIN_VALUE + " to " + Short.MAX_VALUE);
        Serial.println("int: " + Integer.BYTES + " bytes, range: " + Integer.MIN_VALUE + " to " + Integer.MAX_VALUE);
        Serial.println("long: " + Long.BYTES + " bytes, range: " + Long.MIN_VALUE + " to " + Long.MAX_VALUE);
        Serial.println("float: " + Float.BYTES + " bytes, range: " + Float.MIN_VALUE + " to " + Float.MAX_VALUE);
        Serial.println("double: " + Double.BYTES + " bytes, range: " + Double.MIN_VALUE + " to " + Double.MAX_VALUE);
    }
}
