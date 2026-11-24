package io.github.jabrena.juno.maven.provisioner;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Writes named secrets into the board's secret store, on request from the host. The host sends one line per
 * request over USB serial, so the value never goes on the command line or onto the SD card:
 * <ul>
 * <li>{@code SET <name> <hex>}: stores the value, given as hex digits, under any name of 1 to 31 printable characters,
 * and answers {@code OK <name>}. The name shares a 63-byte record with the value, so the value can be up to
 * 62 bytes minus the name's length.</li>
 * <li>{@code CLEAR <name>}: empties the name and answers {@code OK <name>}.</li>
 * <li>{@code CLEAR ALL}: empties every data slot on the chip, including names other programs wrote, and
 * answers {@code OK CLEAR ALL}. It decommissions the chip rather than this
 * program's names.</li>
 * <li>{@code LIST <name>}: answers {@code STATE <name> written} or {@code STATE <name> missing}. No value is printed.
 * The store cannot enumerate what it holds, so the host names every key it asks about; the program knows no names of
 * its own.</li>
 * </ul>
 * A bad request answers {@code ERR} with a short reason and stores nothing. The board prints {@code READY} once it can
 * take requests, and again every second until the first request arrives: a board's USB serial port can come back after
 * an upload later than the program starts, and a line printed before the host opens the port is lost. The store keeps each name next to its value, so a reading program only has to ask for the same name.
 * The {@code juno-maven-plugin} ships this program and {@code juno:secret} uploads it when no {@code -Djuno.main} is
 * given, so any project can provision a board without a copy of its own.
 */
@Board({ArduinoPortentaH7Board.class, ArduinoNanoEsp32Board.class, RaspberryPico2WifiBoard.class,
        M5StackCardputerBoard.class, AiThinkerEsp32CamBoard.class, ArduinoUnoR4WiFiBoard.class})
public final class SecretProvisioner {
    private static final int LINE_BYTES = 160;
    private static final int VALUE_BYTES = 63;
    private static final int NAME_PREFIX_SET = 4;
    private static final int NAME_PREFIX_CLEAR = 6;
    private static final int NAME_PREFIX_LIST = 5;
    private static final int POLL_MS = 5;
    private static final int READY_REPEAT_MS = 1000;
    private final SecretStore chip;
    private final byte[] line = new byte[LINE_BYTES];
    private final byte[] value = new byte[VALUE_BYTES];
    private final byte[] readBack = new byte[VALUE_BYTES];
    private int lineLength;
    private boolean lineTooLong;
    private boolean requested;

    SecretProvisioner(SecretStore chip) {
        this.chip = chip;
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        new SecretProvisioner(MCU.getSecretStore()).run();
    }

    private void run() {
        Serial.println("READY");
        while (true) {
            waitForInput();
            if (accept(Serial.read())) {
                Serial.println(answer());
            }
        }
    }

    /** Waits for a received byte, printing {@code READY} every second until the first request arrives. */
    private void waitForInput() {
        int idleMs = 0;
        while (Serial.available() == 0) {
            Delay.millis(POLL_MS);
            idleMs += POLL_MS;
            if (!requested && idleMs >= READY_REPEAT_MS) {
                Serial.println("READY");
                idleMs = 0;
            }
        }
        requested = true;
    }

    /**
     * Takes one received byte into {@link #line}: true once a newline ends the request. A carriage return is skipped,
     * and bytes past the line's capacity are dropped and flagged.
     */
    boolean accept(int received) {
        if (received == '\n') {
            return true;
        }
        if (received != '\r') {
            if (lineLength < LINE_BYTES) {
                line[lineLength] = (byte) received;
                lineLength++;
            } else {
                lineTooLong = true;
            }
        }
        return false;
    }

    /** The reply to the request line {@link #accept(int)} completed, which it then clears for the next request. */
    String answer() {
        String reply = lineTooLong ? "ERR line too long" : handle();
        lineLength = 0;
        lineTooLong = false;
        return reply;
    }

    private String handle() {
        if (startsWith("SET ")) {
            return set();
        }
        if (startsWith("CLEAR ALL")) {
            return clearAll();
        }
        if (startsWith("CLEAR ")) {
            return clear();
        }
        if (startsWith("LIST")) {
            return list();
        }
        return "ERR unknown request";
    }

    private String set() {
        int nameEnd = indexOfSpace(NAME_PREFIX_SET);
        if (nameEnd <= NAME_PREFIX_SET) {
            return "ERR missing name";
        }
        String name = text(NAME_PREFIX_SET, nameEnd);
        int hexStart = nameEnd + 1;
        int hexLength = lineLength - hexStart;
        if (hexLength <= 0 || hexLength % 2 != 0 || hexLength / 2 > VALUE_BYTES) {
            return "ERR bad value length";
        }
        if (!decodeHex(hexStart, hexLength)) {
            return "ERR bad hex value";
        }
        if (chip.write(name, value, hexLength / 2) < 0) {
            return "ERR store rejected the write: bad name, too long or store full";
        }
        return "OK " + name;
    }

    private String clear() {
        String name = text(NAME_PREFIX_CLEAR, lineLength);
        if (chip.clear(name) != 0) {
            return "ERR write failed";
        }
        return "OK " + name;
    }

    private String clearAll() {
        if (lineLength != "CLEAR ALL".length()) {
            return "ERR unknown request";
        }
        if (chip.clearAll() != 0) {
            return "ERR write failed";
        }
        return "OK CLEAR ALL";
    }

    private String list() {
        if (lineLength <= NAME_PREFIX_LIST || line[NAME_PREFIX_LIST - 1] != ' ') {
            return "ERR missing name";
        }
        String name = text(NAME_PREFIX_LIST, lineLength);
        boolean written = chip.read(name, readBack) >= 0;
        return "STATE " + name + (written ? " written" : " missing");
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
