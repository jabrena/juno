package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Prints the bytes received from a LEGO Mindstorms 9738 remote through an HX-M121 receiver. */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class RcxRemoteControlRawProbe {
    private static final int RECEIVE_PIN = 2;
    private static final int NO_PIN = -1;
    private static final int RCX_BAUD = 2400;
    private static final int FIRST_BYTE_TIMEOUT_MICROS = 20000;
    private static final int NEXT_BYTE_TIMEOUT_MICROS = 3000;
    private static final int FRAME_CAPACITY = 16;
    private static final int START_MILLIS = 2000;
    private static final int[] FRAME = new int[FRAME_CAPACITY];

    private RcxRemoteControlRawProbe() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Infrared.beginHX(RECEIVE_PIN, NO_PIN, RCX_BAUD);
        Delay.millis(START_MILLIS);
        Serial.println("RCX raw probe: press a LEGO 9738 button");

        while (true) {
            printFrame();
        }
    }

    /** Captures a frame without serial I/O, then prints it after the infrared burst ends. */
    private static void printFrame() {
        int value = Infrared.readByte(FIRST_BYTE_TIMEOUT_MICROS);
        if (value == Infrared.TIMEOUT) {
            return;
        }
        if (value == Infrared.FRAMING_ERROR) {
            Serial.println("framing error");
            return;
        }

        int count = 0;
        FRAME[count++] = value;
        boolean framingError = false;
        while (count < FRAME_CAPACITY) {
            value = Infrared.readByte(NEXT_BYTE_TIMEOUT_MICROS);
            if (value == Infrared.TIMEOUT) {
                break;
            }
            if (value == Infrared.FRAMING_ERROR) {
                framingError = true;
                break;
            }
            FRAME[count++] = value;
        }

        Serial.print("RCX raw:");
        for (int index = 0; index < count; index++) {
            printHex(FRAME[index]);
        }
        if (framingError) {
            Serial.print(" framing error");
        }
        Serial.println("");
    }

    /** Prints one byte in hexadecimal without creating a runtime string. */
    private static void printHex(int value) {
        Serial.print(" ");
        printHexDigit((value >>> 4) & 0x0F);
        printHexDigit(value & 0x0F);
    }

    private static void printHexDigit(int value) {
        if (value < 10) {
            Serial.print(value);
        } else if (value == 10) {
            Serial.print("A");
        } else if (value == 11) {
            Serial.print("B");
        } else if (value == 12) {
            Serial.print("C");
        } else if (value == 13) {
            Serial.print("D");
        } else if (value == 14) {
            Serial.print("E");
        } else {
            Serial.print("F");
        }
    }
}
