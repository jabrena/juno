package demo;

import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/** Every Serial print overload. */
public final class SerialApi {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        
        Serial.println(true);
        Serial.println(42);
        Serial.println(42L);
        Serial.println(1.5f);
        Serial.println(2.5);
        Serial.print("value=");
        Serial.println("text");
    }
}
