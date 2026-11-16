package io.github.jabrena.juno.api.hardware.security;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Verifies {@link SecretStore#clearAll()} against the real chip — the NXP SE050C2 on the Portenta H7 — from the board
 * alone, with no provisioning from the host: it writes two dummy names itself, reads both back, wipes the chip, and
 * then checks that both names report missing.
 *
 * <p><b>This program empties every data slot on the chip.</b> {@code clearAll} decommissions the secure element
 * rather than the names one program wrote, so a provisioned {@code wifi.ssid} and {@code wifi.password} are erased too and
 * have to be provisioned again afterwards:
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml juno:secret \
 *     -Djuno.main=io.github.jabrena.juno.api.hardware.security.SecretProvisioner \
 *     -Djuno.board=arduino-portenta-h7 -Djuno.port=<serial-port> -Djuno.secret.key=wifi.ssid
 * }</pre>
 *
 * <p>Nothing secret is involved on the way in: both values are dummy ASCII constants in the firmware, and the two
 * names take whichever slots are free — the wipe reaches every slot regardless. {@link SecretStoreCheck} is the non-destructive counterpart: it checks writes, updates,
 * per-name {@code clear} and the longest value without ever calling {@code clearAll}.
 *
 * <p>The three steps run once at startup, because a secure element has a finite write endurance, and the results are
 * reprinted every two seconds so a monitor attached late still sees them. Each is printed as {@code <name> 1} for
 * pass or {@code <name> 0} for fail:
 *
 * <ol>
 *   <li>{@code written}: both dummy values are stored and read back byte for byte;</li>
 *   <li>{@code cleared}: {@code clearAll} reports success, having tried every slot;</li>
 *   <li>{@code missing}: a read of each name afterwards reports it missing.</li>
 * </ol>
 *
 * <p>{@code failures 0} means the wipe worked. That the chip stays wiped across a reboot is worth one more check from
 * the host, which prints every name as {@code missing} without reading any value:
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:secret -Djuno.secret.list=true \
 *     -Djuno.main=io.github.jabrena.juno.api.hardware.security.SecretProvisioner \
 *     -Djuno.board=arduino-portenta-h7 -Djuno.port=<serial-port>
 * }</pre>
 */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaSecretWipe {
    /** The largest value Juno's secure-store providers accept. */
    private static final int MAX_VALUE_BYTES = 63;
    private static final String FIRST_NAME = "dummy.one";
    private static final String SECOND_NAME = "dummy.two";
    private static final String FIRST_VALUE = "dummy-value-one";
    private static final String SECOND_VALUE = "dummy-value-two";

    private final SecretStore chip;
    private final byte[] first = new byte[MAX_VALUE_BYTES];
    private final byte[] second = new byte[MAX_VALUE_BYTES];
    private final byte[] readBack = new byte[MAX_VALUE_BYTES];
    private final int firstLength;
    private final int secondLength;
    private boolean written;
    private boolean cleared;
    private boolean missing;

    PortentaSecretWipe(SecretStore chip) {
        this.chip = chip;
        firstLength = copyAscii(FIRST_VALUE, first);
        secondLength = copyAscii(SECOND_VALUE, second);
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        // Two dummy names of this program's own; clearAll reaches every slot either way.
        PortentaSecretWipe example = new PortentaSecretWipe(MCU.getSecretStore());
        example.check();
        example.report();
    }

    /** Writes both dummy names, wipes the chip, and checks that both are gone. */
    private void check() {
        written = chip.write(FIRST_NAME, first, firstLength) == firstLength
                && chip.write(SECOND_NAME, second, secondLength) == secondLength
                && readsBack(FIRST_NAME, first, firstLength)
                && readsBack(SECOND_NAME, second, secondLength);
        cleared = chip.clearAll() == 0;
        missing = isMissing(FIRST_NAME) && isMissing(SECOND_NAME);
    }

    /** Reprints the results for as long as the board runs, allocating nothing. */
    private void report() {
        int failures = count(written) + count(cleared) + count(missing);
        while (true) {
            Serial.print("written ");
            Serial.println(flag(written));
            Serial.print("cleared ");
            Serial.println(flag(cleared));
            Serial.print("missing ");
            Serial.println(flag(missing));
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

    /** Copies {@code text}'s characters into {@code buffer} as ASCII bytes and returns how many. */
    private static int copyAscii(String text, byte[] buffer) {
        for (int i = 0; i < text.length(); i++) {
            buffer[i] = (byte) text.charAt(i);
        }
        return text.length();
    }

    private static int flag(boolean passed) {
        return passed ? 1 : 0;
    }

    private static int count(boolean passed) {
        return passed ? 0 : 1;
    }
}
