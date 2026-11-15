package io.github.jabrena.juno.api.hardware.security;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.security.SecretSlots;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.security.SecretStoreFactory;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Checks the write, update, clear and {@code clearAll} behaviour of the board's secure store against the real chip —
 * the NXP SE050C2 on the Portenta H7 — and prints one line per check over USB serial.
 *
 * <p>The checks run <b>once</b> at startup and the results are then reprinted every two seconds, so a monitor
 * attached late still sees them. Running them once is deliberate: every secure element has a finite write
 * endurance, and a loop that rewrote an object thousands of times would wear it out for no extra information.
 *
 * <p>This program uses a table of its own three test names on slots 2 to 4, which {@link ExampleSecrets} and
 * {@link SecretProvisioner} leave free. That matters for the last checks: {@code clearAll} empties only the names in
 * the table of the store that runs it, so the provisioned {@code wifi.ssid} and {@code wifi.password} on slots 8 and
 * 9 are never touched here. The test values are constants in the firmware, not secrets. The last check empties the
 * test names again, so a run leaves no test data on the chip.
 *
 * <p>The checks, in order, each printed as {@code <name> 1} for pass or {@code <name> 0} for fail:
 *
 * <ol>
 *   <li>{@code write-read}: a 5-byte value is stored and read back byte for byte;</li>
 *   <li>{@code update}: the same name is overwritten with a shorter, different value, and a read returns the new
 *       value at its new length — the old bytes must not survive;</li>
 *   <li>{@code clear}: the name is emptied and a read then reports it missing;</li>
 *   <li>{@code rewrite}: a write to the name that was just cleared succeeds and reads back. This is the check that
 *       the provider left the object <em>unlocked</em>: the SE050C2 deletes and recreates an object to replace its
 *       value, and an object left locked or policy-restricted would refuse this write;</li>
 *   <li>{@code long-value}: the 63-byte maximum the providers share round-trips at full length;</li>
 *   <li>{@code clear-all}: {@code clearAll} empties every name in this program's table, and all three then report
 *       missing;</li>
 *   <li>{@code rewrite-after-clear-all}: a name is written and read once more, so {@code clearAll} is shown to
 *       leave the objects usable too;</li>
 *   <li>{@code left-clean}: the test names are emptied a final time and all report missing.</li>
 * </ol>
 *
 * <p>{@code failures 0} on the summary line means every check passed. Flash it and read the output:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *     -Djuno.main=io.github.jabrena.juno.api.hardware.security.PortentaSecretStore \
 *     -Djuno.board=arduino-portenta-h7
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-portenta-h7 -Djuno.baudRate=115200
 * }</pre>
 */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaSecretStore {
    /** The largest value Juno's secure-store providers accept. */
    private static final int MAX_VALUE_BYTES = 63;
    private static final int FIRST_LENGTH = 5;
    private static final int SECOND_LENGTH = 3;

    private static final String SHORT_NAME = "test.short";
    private static final String LONG_NAME = "test.long";
    private static final String SPARE_NAME = "test.spare";

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
    private boolean clearedAll;
    private boolean rewriteAfterClearAll;
    private boolean leftClean;

    PortentaSecretStore(SecretStore chip) {
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
        I2c i2c = MCU.getI2c();
        // Three names of this program's own, on slots the provisioned tables leave free.
        SecretSlots slots = new SecretSlots()
                .add(SHORT_NAME, 2)
                .add(LONG_NAME, 3)
                .add(SPARE_NAME, 4);
        PortentaSecretStore example =
                new PortentaSecretStore(SecretStoreFactory.open(MCU.getBoardModel(), i2c, slots));
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
        longRoundTrip = chip.write(LONG_NAME, longValue, MAX_VALUE_BYTES) == MAX_VALUE_BYTES
                && readsBack(LONG_NAME, longValue, MAX_VALUE_BYTES);
        clearedAll = chip.clearAll() == 0
                && isMissing(SHORT_NAME) && isMissing(LONG_NAME) && isMissing(SPARE_NAME);
        rewriteAfterClearAll = chip.write(SPARE_NAME, second, SECOND_LENGTH) == SECOND_LENGTH
                && readsBack(SPARE_NAME, second, SECOND_LENGTH);
        leftClean = chip.clearAll() == 0
                && isMissing(SHORT_NAME) && isMissing(LONG_NAME) && isMissing(SPARE_NAME);
    }

    /** Reprints the results for as long as the board runs, allocating nothing. */
    private void report() {
        int failures = count(writeRead) + count(update) + count(cleared) + count(rewrite)
                + count(longRoundTrip) + count(clearedAll) + count(rewriteAfterClearAll) + count(leftClean);
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
            Serial.print("clear-all ");
            Serial.println(flag(clearedAll));
            Serial.print("rewrite-after-clear-all ");
            Serial.println(flag(rewriteAfterClearAll));
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
