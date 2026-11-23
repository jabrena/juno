package spike;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** The method the Portenta M4 spike runs: integer loop, branches, remainder, no allocation and no runtime calls. */
@Board(ArduinoPortentaH7Board.class)
public final class SpikeMethod {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_9600);
        Serial.println(compute(args.length + 1000));
    }

    static int compute(int n) {
        int total = 0;
        for (int i = 1; i <= n; i++) {
            total += (i % 3 == 0) ? i * 2 : i;
        }
        return total;
    }
}
