package io.github.jabrena.juno;

import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

public class DataTypes {
    public static void main(String[] args) {
        /* Data types demo
        System.out.println("Data types demo");
        System.out.println("byte: " + Byte.BYTES + " bytes, range: " + Byte.MIN_VALUE + " to " + Byte.MAX_VALUE);
        System.out.println("short: " + Short.BYTES + " bytes, range: " + Short.MIN_VALUE + " to " + Short.MAX_VALUE);
        System.out.println("int: " + Integer.BYTES + " bytes, range: " + Integer.MIN_VALUE + " to " + Integer.MAX_VALUE);
        System.out.println("long: " + Long.BYTES + " bytes, range: " + Long.MIN_VALUE + " to " + Long.MAX_VALUE);
        System.out.println("float: " + Float.BYTES + " bytes, range: " + Float.MIN_VALUE + " to " + Float.MAX_VALUE);
        System.out.println("double: " + Double.BYTES + " bytes, range: " + Double.MIN_VALUE + " to " + Double.MAX_VALUE);
        */

        Serial.println("Data types demo");
        Serial.print("byte: ");
        Serial.print(Byte.BYTES);
        Serial.print(" bytes, range: ");
        Serial.print(Byte.MIN_VALUE);
        Serial.print(" to ");
        Serial.println(Byte.MAX_VALUE);
        Serial.print("short: ");
        Serial.print(Short.BYTES);
        Serial.print(" bytes, range: ");
        Serial.print(Short.MIN_VALUE);
        Serial.print(" to ");
        Serial.println(Short.MAX_VALUE);
        Serial.print("int: ");
        Serial.print(Integer.BYTES);
        Serial.print(" bytes, range: ");
        Serial.print(Integer.MIN_VALUE);
        Serial.print(" to ");
        Serial.println(Integer.MAX_VALUE);
        Serial.print("long: ");
        Serial.print(Long.BYTES);
        Serial.print(" bytes, range: ");
        Serial.print(Long.MIN_VALUE);
        Serial.print(" to ");
        Serial.println(Long.MAX_VALUE);
        Serial.print("float: ");
        Serial.print(Float.BYTES);
        Serial.print(" bytes, range: ");
        Serial.print(Float.MIN_VALUE);
        Serial.print(" to ");
        Serial.println(Float.MAX_VALUE);
        Serial.print("double: ");
        Serial.print(Double.BYTES);
        Serial.print(" bytes, range: ");
        Serial.print(Double.MIN_VALUE);
        Serial.print(" to ");
        Serial.println(Double.MAX_VALUE);
    }
}
