package io.github.jabrena.juno.api.thirdparty.lego.rcx;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.thirdparty.ir.Infrared;
import io.github.jabrena.juno.util.Delay;

/** Sends the RCX firmware-independent alive packet and prints the raw reply. */
public class RcxRawSoundProbe {
    private static final Clock CLOCK = MCU.getClock();
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int BAUD = 2400;
    private static final int REPLY_READS = 7;
    private static final int REPLY_POLL_MICROS = 20000;
    private static final int SERIAL_DRAIN_MILLIS = 20;
    private static final int[] REPLIES = new int[REPLY_READS];

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.seconds(2);
        Infrared.begin(RECEIVE_PIN, TRANSMIT_PIN, BAUD);
        Serial.println("Sending raw RCX alive packet");
        Delay.millis(SERIAL_DRAIN_MILLIS);

        Infrared.writeByte(0x55);
        Infrared.writeByte(0xFF);
        Infrared.writeByte(0x00);
        // The first new command must have the RCX protocol's toggle bit set. A clear toggle is the
        // receiver's initial state and can therefore be discarded as a repeated command.
        Infrared.writeByte(0x18);
        Infrared.writeByte(0xE7);
        Infrared.writeByte(0x18);
        Infrared.writeByte(0xE7);

        int started = CLOCK.micros();
        for (int i = 0; i < REPLY_READS; i++) {
            REPLIES[i] = Infrared.readByte(REPLY_POLL_MICROS);
        }
        int elapsed = CLOCK.micros() - started;

        Serial.print("Reply:");
        for (int i = 0; i < REPLY_READS; i++) {
            Serial.print(" ");
            Serial.print(REPLIES[i]);
        }
        Serial.println("");
        Serial.print("Read elapsed us: ");
        Serial.println(elapsed);
    }
}
