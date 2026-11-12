package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Psram;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Exercises the static PSRAM intrinsic surface without exposing a runtime service object. */
@Board(ArduinoNanoEsp32Board.class)
public final class PsramApi {
    private PsramApi() {
    }

    public static void main(String[] args) {
        int handle = Psram.allocate(64);
        Serial.println(handle >= 0);
        if (handle >= 0) {
            byte[] data = new byte[4];
            data[0] = 1;
            data[1] = 2;
            data[2] = 3;
            data[3] = 4;
            Serial.println(Psram.write(handle, 0, data, 0, data.length));
            Serial.println(Psram.readByte(handle, 2));
            Serial.println(Psram.release(handle));
        }
    }
}
