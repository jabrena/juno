package io.github.jabrena.juno.api.hardware.security;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Write, read, {@link SecretStore#clearAll()}, read: the last read must fail. The sequence runs once at startup, and its
 * results are reprinted every two seconds so a monitor attached late still sees them:
 *
 * <ol>
 *   <li>{@code write}: the demo name is stored; the length is printed;</li>
 *   <li>{@code read-before}: the value reads back byte for byte;</li>
 *   <li>{@code clearAll}: the chip is wiped; {@code 0} means every slot was cleared;</li>
 *   <li>{@code read-after}: the same name now reads as missing, a negative code, so {@code failed-as-expected 1}.</li>
 * </ol>
 *
 * <p><b>This program empties every data slot on the chip.</b> {@code clearAll} also erases the Wi-Fi credentials and the
 * SMTP account provisioned with {@code juno:secret}, so they have to be provisioned again afterwards. The demo value is a
 * dummy constant in the firmware; no secret is involved. {@link PortentaSecretWipe} runs the same wipe with two dummy
 * names of its own.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *     -Djuno.main=io.github.jabrena.juno.api.hardware.security.PortentaSecretClearAllRead \
 *     -Djuno.board=arduino-portenta-h7
 * }</pre>
 */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaSecretClearAllRead {
    /** The largest value Juno's secure-store providers accept. */
    private static final int MAX_VALUE_BYTES = 63;
    private static final String NAME = "demo.clearall";
    private static final String VALUE = "before-clearall";
    private static final int REPORT_INTERVAL_MS = 2000;

    private final SecretStore chip;
    private final byte[] value = new byte[MAX_VALUE_BYTES];
    private final byte[] readBack = new byte[MAX_VALUE_BYTES];
    private int writeResult;
    private int readBeforeResult;
    private boolean matchedBefore;
    private int clearAllResult;
    private int readAfterResult;

    private PortentaSecretClearAllRead(SecretStore chip) {
        this.chip = chip;
        copyAscii(VALUE, value);
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        PortentaSecretClearAllRead example =
                new PortentaSecretClearAllRead(MCU.getSecretStore());
        example.run();
        while (true) {
            example.report();
            Delay.millis(REPORT_INTERVAL_MS);
        }
    }

    private void run() {
        writeResult = chip.write(NAME, value, VALUE.length());
        readBeforeResult = chip.read(NAME, readBack);
        matchedBefore = readBeforeResult == VALUE.length() && sameBytes(readBack, VALUE.length());
        clearAllResult = chip.clearAll();
        readAfterResult = chip.read(NAME, readBack);
    }

    private void report() {
        Serial.print("write=");
        Serial.println(writeResult);
        Serial.print("read-before=");
        Serial.print(readBeforeResult);
        Serial.print(" matched=");
        Serial.println(flag(matchedBefore));
        Serial.print("clearAll=");
        Serial.println(clearAllResult);
        Serial.print("read-after=");
        Serial.print(readAfterResult);
        Serial.print(" failed-as-expected=");
        Serial.println(flag(readAfterResult < 0));
    }

    private boolean sameBytes(byte[] bytes, int length) {
        for (int i = 0; i < length; i++) {
            if (bytes[i] != value[i]) {
                return false;
            }
        }
        return true;
    }

    private static void copyAscii(String text, byte[] buffer) {
        for (int i = 0; i < text.length(); i++) {
            buffer[i] = (byte) text.charAt(i);
        }
    }

    private static int flag(boolean passed) {
        return passed ? 1 : 0;
    }
}
