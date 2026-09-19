import io.github.jabrena.juno.api.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.api.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Serial;

/**
 * Counts up once a second, printing each value over USB serial, readable with:
 * {@code arduino-cli monitor -p /dev/cu.usbmodemACA704344CE82 -c baudrate=9600}.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class SerialCounter {
    private static final int BAUD_RATE = 9600;

    public static void main(String[] args) {
        Serial.begin(BAUD_RATE);

        int counter = 0;
        while (true) {
            Serial.println(counter);
            counter = counter + 1;
            Delay.millis(1000);
        }
    }
}
