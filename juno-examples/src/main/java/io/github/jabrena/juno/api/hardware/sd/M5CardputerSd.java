package io.github.jabrena.juno.api.hardware.sd;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.M5StackCardputer;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Initializes the Cardputer's built-in microSD socket and reports the result over USB serial. */
@Board(io.github.jabrena.juno.annotations.M5StackCardputer.class)
public final class M5CardputerSd {
    private M5CardputerSd() {
    }

    /** Starts the USB serial port and the built-in microSD socket. */
    public static void main(String[] args) {
        Serial.begin(9600);
        Delay.millis(1000);
        SdCard sd = M5StackCardputer.getSd();
        if (sd.begin()) {
            Serial.println("SD card ready");
        } else {
            Serial.println("SD card unavailable");
        }
    }
}
