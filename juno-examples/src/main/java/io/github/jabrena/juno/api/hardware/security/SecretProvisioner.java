package io.github.jabrena.juno.api.hardware.security;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.security.Atecc608;
import io.github.jabrena.juno.hardware.security.SecretSlots;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Writes named secrets into the ATECC608 on the board's I2C bus, on request from the host. The host sends one line per
 * request over USB serial, so the value never goes on the command line or onto the SD card:
 * <ul>
 * <li>{@code SET <name> <hex>}: stores the value, given as hex digits (at most 63 bytes), and answers {@code OK <name>}.</li>
 * <li>{@code CLEAR <name>}: empties the name and answers {@code OK <name>}.</li>
 * <li>{@code CLEAR ALL}: empties every name in this program's {@link SecretSlots} table and answers {@code OK CLEAR ALL}.</li>
 * <li>{@code LIST}: answers {@code STATE <name> written|missing} for each name, then {@code END}. No value is printed.</li>
 * </ul>
 * A bad request answers {@code ERR} with a short reason and stores nothing. The board prints {@code READY} once it can
 * take requests. The name-to-slot table here must match the reading program's table: it is the only place slot numbers
 * are chosen. Upload this program with {@code juno:secret}.
 */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class SecretProvisioner {
    private static final int LINE_BYTES = 160;
    private static final int VALUE_BYTES = 63;
    private static final int NAME_PREFIX_SET = 4;
    private static final int NAME_PREFIX_CLEAR = 6;

    private final Atecc608 chip;
    private final SecretSlots slots;
    private final byte[] line = new byte[LINE_BYTES];
    private final byte[] value = new byte[VALUE_BYTES];
    private final byte[] readBack = new byte[VALUE_BYTES];
    private int lineLength;
    private boolean lineTooLong;

    SecretProvisioner(Atecc608 chip, SecretSlots slots) {
        this.chip = chip;
        this.slots = slots;
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        I2c i2c = MCU.getI2c();
        i2c.begin();
        SecretSlots slots = new SecretSlots()
                .add("wifi.ssid", 8)
                .add("wifi.password", 9)
                .add("mailbox.apikey", 10)
                .add("mailbox.q.ip", 0)
                .add("smtp.host", 11)
                .add("smtp.username", 12)
                .add("smtp.password", 13)
                .add("supabase.api.host", 14)
                .add("supabase.api.key", 15)
                .add("supabase.api.url", 1);
        new SecretProvisioner(new Atecc608(i2c, slots), slots).run();
    }

    private void run() {
        Serial.println("READY");
        while (true) {
            readLine();
            if (lineTooLong) {
                Serial.println("ERR line too long");
            } else {
                handle();
            }
        }
    }

    /** Reads one request line into {@link #line}; bytes past its capacity are dropped and flagged. */
    private void readLine() {
        lineLength = 0;
        lineTooLong = false;
        while (true) {
            while (Serial.available() == 0) {
                Delay.millis(5);
            }
            int received = Serial.read();
            if (received == '\n') {
                return;
            }
            if (received == '\r') {
                continue;
            }
            if (lineLength < LINE_BYTES) {
                line[lineLength] = (byte) received;
                lineLength++;
            } else {
                lineTooLong = true;
            }
        }
    }

    private void handle() {
        if (startsWith("SET ")) {
            set();
        } else if (startsWith("CLEAR ALL")) {
            clearAll();
        } else if (startsWith("CLEAR ")) {
            clear();
        } else if (startsWith("LIST")) {
            list();
        } else {
            Serial.println("ERR unknown request");
        }
    }

    private void set() {
        int nameEnd = indexOfSpace(NAME_PREFIX_SET);
        if (nameEnd <= NAME_PREFIX_SET) {
            Serial.println("ERR missing name");
            return;
        }
        String name = text(NAME_PREFIX_SET, nameEnd);
        if (!slots.contains(name)) {
            Serial.println("ERR unknown name");
            return;
        }
        int hexStart = nameEnd + 1;
        int hexLength = lineLength - hexStart;
        if (hexLength <= 0 || hexLength % 2 != 0 || hexLength / 2 > VALUE_BYTES) {
            Serial.println("ERR bad value length");
            return;
        }
        if (!decodeHex(hexStart, hexLength)) {
            Serial.println("ERR bad hex value");
            return;
        }
        if (chip.write(name, value, hexLength / 2) < 0) {
            Serial.println("ERR write failed");
            return;
        }
        Serial.println("OK " + name);
    }

    private void clear() {
        String name = text(NAME_PREFIX_CLEAR, lineLength);
        if (!slots.contains(name)) {
            Serial.println("ERR unknown name");
            return;
        }
        if (chip.clear(name) != 0) {
            Serial.println("ERR write failed");
            return;
        }
        Serial.println("OK " + name);
    }

    private void clearAll() {
        if (lineLength != "CLEAR ALL".length()) {
            Serial.println("ERR unknown request");
            return;
        }
        if (chip.clearAll() != 0) {
            Serial.println("ERR write failed");
            return;
        }
        Serial.println("OK CLEAR ALL");
    }

    private void list() {
        for (int i = 0; i < slots.count(); i++) {
            String name = slots.nameAt(i);
            boolean written = chip.read(name, readBack) >= 0;
            Serial.println("STATE " + name + (written ? " written" : " missing"));
        }
        Serial.println("END");
    }

    private boolean startsWith(String prefix) {
        if (lineLength < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (line[i] != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private int indexOfSpace(int from) {
        for (int i = from; i < lineLength; i++) {
            if (line[i] == ' ') {
                return i;
            }
        }
        return -1;
    }

    private String text(int start, int end) {
        StringBuilder text = new StringBuilder(end - start);
        for (int i = start; i < end; i++) {
            text.append((char) line[i]);
        }
        return text.toString();
    }

    /** Decodes {@code hexLength} hex digits starting at {@code start} into {@link #value}; false on a non-hex digit. */
    private boolean decodeHex(int start, int hexLength) {
        for (int i = 0; i < hexLength / 2; i++) {
            int high = hexDigit(line[start + 2 * i]);
            int low = hexDigit(line[start + 2 * i + 1]);
            if (high < 0 || low < 0) {
                return false;
            }
            value[i] = (byte) (high * 16 + low);
        }
        return true;
    }

    private static int hexDigit(int digit) {
        if (digit >= '0' && digit <= '9') {
            return digit - '0';
        }
        if (digit >= 'a' && digit <= 'f') {
            return digit - 'a' + 10;
        }
        if (digit >= 'A' && digit <= 'F') {
            return digit - 'A' + 10;
        }
        return -1;
    }
}
