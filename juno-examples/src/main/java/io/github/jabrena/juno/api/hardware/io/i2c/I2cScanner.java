package io.github.jabrena.juno.api.hardware.io.i2c;

import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.hardware.io.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.io.serial.Serial;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Scans the board's primary I2C bus ({@code Wire}, the SDA/SCL pins) and prints every 7-bit
 * address that answers, in decimal, then repeats every few seconds. Use it to check that a shield
 * or sensor is reachable before debugging its driver: the Arduino 9 Axis Motion Shield's BNO055,
 * for example, answers at {@code 40} ({@code 0x28}) or {@code 41} ({@code 0x29}).
 *
 * <pre>{@code
 * I2C scan
 * found 40
 * devices 1
 * }</pre>
 *
 * <p>Pick the board with {@code -Djuno.board}.
 */
public final class I2cScanner {
    private static final int FIRST_ADDRESS = 1;
    private static final int LAST_ADDRESS = 127;
    private static final int SCAN_PERIOD_MILLIS = 3000;

    private I2cScanner() {
    }

    /**
     * Starts the bus and scans it indefinitely.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);
        I2c i2c = MCU.getI2c();
        i2c.begin();

        while (true) {
            Serial.println("I2C scan");
            int devices = 0;
            for (int address = FIRST_ADDRESS; address <= LAST_ADDRESS; address++) {
                if (i2c.readRegister(address, 0) >= 0) {
                    Serial.print("found ");
                    Serial.println(address);
                    devices++;
                }
            }
            Serial.print("devices ");
            Serial.println(devices);
            Delay.millis(SCAN_PERIOD_MILLIS);
        }
    }
}
