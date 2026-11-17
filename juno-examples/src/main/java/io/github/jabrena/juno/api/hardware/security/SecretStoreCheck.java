package io.github.jabrena.juno.api.hardware.security;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
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
 * Checks the write, update, clear and rewrite behaviour of whatever store the board actually has, and prints one
 * line per check over USB serial. {@link MCU#getSecretStore} decides which that is — the Portenta H7's NXP SE050C2,
 * an ESP32 board's own non-volatile storage, or the Pico 2 W's slots behind Juno's TrustZone secure firmware — so the
 * same checks run against each through {@link SecretStore} alone.
 *
 * <p>The checks run <b>once</b> at startup and the results are then reprinted every two seconds, so a monitor
 * attached late still sees them. Running them once is deliberate: a secure element and a flash partition both have a
 * finite write endurance, and a loop that rewrote an entry thousands of times would wear it out for no extra
 * information.
 *
 * <p>This program uses three test names of its own, which take free slots while it runs, and it empties them with
 * {@code clear} per name when it is done, so a run leaves no test data in the store and never touches the provisioned
 * {@code wifi.ssid} and {@code wifi.password}. The store needs three free slots. The test values are constants in
 * the firmware, not secrets.
 *
 * <p>{@code clearAll} is deliberately <b>not</b> exercised here: it empties every data slot on the chip, not only the
 * names this program wrote, so calling it would decommission the chip and destroy the provisioned Wi-Fi credentials,
 * which can only be restored by typing them again at {@code juno:secret}'s hidden prompt. Verify that path on purpose
 * with {@code juno:secret -Djuno.secret.clear=true}, which asks for the word {@code CLEAR} first.
 *
 * <p>The checks, in order, each printed as {@code <name> 1} for pass or {@code <name> 0} for fail:
 *
 * <ol>
 *   <li>{@code write-read}: a 5-byte value is stored and read back byte for byte;</li>
 *   <li>{@code update}: the same name is overwritten with a shorter, different value, and a read returns the new
 *       value at its new length — the old bytes must not survive;</li>
 *   <li>{@code clear}: the name is emptied and a read then reports it missing;</li>
 *   <li>{@code rewrite}: a write to the name that was just cleared succeeds and reads back. This is the check that
 *       the provider left the entry <em>usable</em>: the SE050C2 deletes and recreates an object to replace its
 *       value, and an object left locked or policy-restricted would refuse this write;</li>
 *   <li>{@code long-value}: the longest value that fits next to its name in a 63-byte record round-trips at full
 *       length;</li>
 *   <li>{@code left-clean}: every test name is emptied with {@code clear} and all three report missing.</li>
 * </ol>
 *
 * <p>{@code failures 0} on the summary line means every check passed. Flash it and read the output:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *     -Djuno.main=io.github.jabrena.juno.api.hardware.security.SecretStoreCheck \
 *     -Djuno.board=<board id>
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=<board id> -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class, M5StackCardputerBoard.class, AiThinkerEsp32CamBoard.class,
        ArduinoPortentaH7Board.class, RaspberryPico2WifiBoard.class,
        ArduinoUnoR4WiFiBoard.class, ArduinoUnoR3Board.class})
public final class SecretStoreCheck {
    /** The bytes one stored secret takes: a length byte, its name and its value. */
    private static final int MAX_VALUE_BYTES = SecretStore.RECORD_BYTES;
    private static final int FIRST_LENGTH = 5;
    private static final int SECOND_LENGTH = 3;

    private static final String SHORT_NAME = "test.short";
    private static final String LONG_NAME = "test.long";
    private static final String SPARE_NAME = "test.spare";
    /** The longest value that fits next to {@link #LONG_NAME}, whose 9 characters share its record. */
    private static final int LONG_LENGTH = SecretStore.RECORD_BYTES - 1 - 9;

    private final SecretStore chip;
    private final byte[] first = new byte[MAX_VALUE_BYTES];
    private final byte[] second = new byte[MAX_VALUE_BYTES];
    private final byte[] longValue = new byte[MAX_VALUE_BYTES];
    private final byte[] readBack = new byte[MAX_VALUE_BYTES];
    private boolean writeRead;
    private boolean update;
    private boolean cleared;
    private boolean rewrite;
    private boolean longRoundTrip;
    private boolean leftClean;

    SecretStoreCheck(SecretStore chip) {
        this.chip = chip;
        for (int i = 0; i < FIRST_LENGTH; i++) {
            first[i] = (byte) (i + 1);
        }
        for (int i = 0; i < SECOND_LENGTH; i++) {
            second[i] = (byte) (0xA0 + i);
        }
        for (int i = 0; i < MAX_VALUE_BYTES; i++) {
            longValue[i] = (byte) (7 * i + 1);
        }
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        SecretStoreCheck example = new SecretStoreCheck(MCU.getSecretStore());
        example.check();
        example.report();
    }

    /** Runs every check once, in order. */
    private void check() {
        writeRead = chip.write(SHORT_NAME, first, FIRST_LENGTH) == FIRST_LENGTH
                && readsBack(SHORT_NAME, first, FIRST_LENGTH);
        update = chip.write(SHORT_NAME, second, SECOND_LENGTH) == SECOND_LENGTH
                && readsBack(SHORT_NAME, second, SECOND_LENGTH);
        cleared = chip.clear(SHORT_NAME) == 0 && isMissing(SHORT_NAME);
        rewrite = chip.write(SHORT_NAME, first, FIRST_LENGTH) == FIRST_LENGTH
                && readsBack(SHORT_NAME, first, FIRST_LENGTH);
        longRoundTrip = chip.write(LONG_NAME, longValue, LONG_LENGTH) == LONG_LENGTH
                && readsBack(LONG_NAME, longValue, LONG_LENGTH);
        // Per name, never clearAll: that would empty every slot of the store, the provisioned Wi-Fi ones included.
        leftClean = chip.clear(SHORT_NAME) == 0 && chip.clear(LONG_NAME) == 0 && chip.clear(SPARE_NAME) == 0
                && isMissing(SHORT_NAME) && isMissing(LONG_NAME) && isMissing(SPARE_NAME);
    }

    /** Reprints the results for as long as the board runs, allocating nothing. */
    private void report() {
        int failures = count(writeRead) + count(update) + count(cleared) + count(rewrite)
                + count(longRoundTrip) + count(leftClean);
        while (true) {
            Serial.print("write-read ");
            Serial.println(flag(writeRead));
            Serial.print("update ");
            Serial.println(flag(update));
            Serial.print("clear ");
            Serial.println(flag(cleared));
            Serial.print("rewrite ");
            Serial.println(flag(rewrite));
            Serial.print("long-value ");
            Serial.println(flag(longRoundTrip));
            Serial.print("left-clean ");
            Serial.println(flag(leftClean));
            Serial.print("failures ");
            Serial.println(failures);
            Delay.millis(2000);
        }
    }

    /** Whether {@code name} reads back as exactly {@code value[0..length)}. */
    private boolean readsBack(String name, byte[] value, int length) {
        if (chip.read(name, readBack) != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (readBack[i] != value[i]) {
                return false;
            }
        }
        return true;
    }

    /** Whether a read of {@code name} reports it missing, which every provider signals with a negative length. */
    private boolean isMissing(String name) {
        return chip.read(name, readBack) < 0;
    }

    private static int flag(boolean passed) {
        return passed ? 1 : 0;
    }

    private static int count(boolean passed) {
        return passed ? 0 : 1;
    }
}
