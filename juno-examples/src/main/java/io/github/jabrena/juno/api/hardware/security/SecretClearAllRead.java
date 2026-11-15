package io.github.jabrena.juno.api.hardware.security;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.security.SecretSlots;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.security.SecretStoreFactory;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Write, read, {@link SecretStore#clearAll()}, read on an ESP32 board's NVS store (Nano ESP32, AI Thinker
 * ESP32-CAM, M5Stack Cardputer) or the Pico 2 W's TrustZone store: the last read must fail. The counterpart of
 * {@link PortentaSecretClearAllRead} for those boards. The sequence runs once at startup, and its results are
 * reprinted every two seconds so a monitor attached late still sees them:
 *
 * <ol>
 *   <li>{@code write}: the demo name is stored; the length is printed;</li>
 *   <li>{@code read-before}: the value reads back byte for byte;</li>
 *   <li>{@code clearAll}: every slot of the store is emptied; {@code 0} means every slot was cleared;</li>
 *   <li>{@code read-after}: the same name now reads as missing, a negative code, so {@code failed-as-expected 1}.</li>
 * </ol>
 *
 * <p><b>This program empties every slot of the board's secret store.</b> {@code clearAll} also erases the Wi-Fi
 * credentials provisioned with {@code juno:secret}, so they have to be provisioned again afterwards. The demo value is
 * a dummy constant in the firmware; no secret is involved.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *     -Djuno.main=io.github.jabrena.juno.api.hardware.security.SecretClearAllRead \
 *     -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 *
 * # AI Thinker ESP32-CAM: -Djuno.board=ai-thinker-esp32-cam -Djuno.port=<usb-serial port>
 * # M5Stack Cardputer: -Djuno.board=m5stack-cardputer
 * # Raspberry Pi Pico 2 W: -Djuno.board=raspberry-pi-pico-2-w
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class, AiThinkerEsp32CamBoard.class, M5StackCardputerBoard.class,
        RaspberryPico2WifiBoard.class})
public final class SecretClearAllRead {
    /** The largest value Juno's secure-store providers accept. */
    private static final int MAX_VALUE_BYTES = 63;
    private static final String NAME = "demo.clearall";
    private static final String VALUE = "before-clearall";
    /** Slot 4 is not used by the other examples' tables. */
    private static final int SLOT = 4;
    private static final int REPORT_INTERVAL_MS = 2000;

    private final SecretStore store;
    private final byte[] value = new byte[MAX_VALUE_BYTES];
    private final byte[] readBack = new byte[MAX_VALUE_BYTES];
    private int writeResult;
    private int readBeforeResult;
    private boolean matchedBefore;
    private int clearAllResult;
    private int readAfterResult;

    private SecretClearAllRead(SecretStore store) {
        this.store = store;
        copyAscii(VALUE, value);
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        SecretSlots slots = new SecretSlots().add(NAME, SLOT);
        SecretClearAllRead example =
                new SecretClearAllRead(SecretStoreFactory.open(slots));
        example.run();
        while (true) {
            example.report();
            Delay.millis(REPORT_INTERVAL_MS);
        }
    }

    private void run() {
        writeResult = store.write(NAME, value, VALUE.length());
        readBeforeResult = store.read(NAME, readBack);
        matchedBefore = readBeforeResult == VALUE.length() && sameBytes(readBack, VALUE.length());
        clearAllResult = store.clearAll();
        readAfterResult = store.read(NAME, readBack);
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
